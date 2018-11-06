package main

import clojure.java.api.Clojure
import clojure.lang.IFn
import clojure.lang.RT
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.commons.jexl3.JexlBuilder
import org.apache.commons.jexl3.MapContext
import java.io.StringReader

class AppInstance {
	private var appSpec: App
	private val creator: String

	constructor(appSpec: App, creator: String) {
		this.appSpec = appSpec
		this.creator = creator
		afterChangeStep()
	}

	fun afterChangeStep() {
	}

	var currentStep: String = "start"

	fun currentStepTask(): Task? {
		return this.appSpec.workflow.tasks.find { it.name == currentStep }
	}

	fun currentStepTaskIndex(): Int {
		return this.appSpec.workflow.tasks.indexOfFirst { it.name == currentStep }
	}

	fun currentAssignee(): String? {
		val currentTask = currentStepTask()
		return when (currentTask) {
			is StartTask -> this.creator
			is TaskWithAssignee -> currentTask.assignees().first()
			else -> null
		}
	}

	fun engine_calc(stanza: String): String? {
		return when (stanza) {
			"current_step" -> currentStep
			"req.creator" -> creator
			else -> null
		}
	}

	fun compareUser(userExperssion: String?, user: String): Boolean {
		return if (userExperssion == user) {
			true
		} else if (userExperssion == "req.initiator" && this.creator == user) {
			true
		} else {
			false
		}
	}

	fun viewAs(current_user: String): String {
		if (compareUser(currentAssignee(), current_user)) {
			val currentStepPermissions = currentStepTask()!!.permissions
			return (listOf("Task::: " + currentStepTask()!!.name) + appSpec.form.sections.map {
				val stepPermissionsForSection = currentStepPermissions.sectionPermissions.get(it.name)
				val effectivePermissionsForSection = when (stepPermissionsForSection) {
					is PermissionLevel -> stepPermissionsForSection
					else -> currentStepPermissions.default
				}


				val fields = when (it) {
					is Section -> {
						it.fields.map {
							"Field: " + it.label + " " + it.id + " " + (it.javaClass) + " " + (if (it.required) "*" else " ")
						}.joinToString("\n")
					}
					else -> {
						"not implemented"
					}
				}

				when (effectivePermissionsForSection) {
					PermissionLevel.HIDDEN -> null
					PermissionLevel.READONLY -> it.name + ":readonly\n" + fields
					PermissionLevel.EDITABLE -> it.name + ":readwrite\n" + fields
				}
			}.filterNotNull()).joinToString("\n")
		} else {
			return "you are not " + currentAssignee()
		}
	}

	data class WriteTemplate(
		val user: String? = null,
		val step_index: Int? = null,
		val data: Map<String, List<ValueContainer<*>>>?
	)

	fun writeTemplate(current_user: String): WriteTemplate {
		return WriteTemplate(current_user, currentStepTaskIndex(), if (compareUser(currentAssignee(), current_user)) {
			val currentStepPermissions = currentStepTask()!!.permissions
			appSpec.form.sections.map { section ->
				val stepPermissionsForSection = currentStepPermissions.sectionPermissions.get(section.name)
				val effectivePermissionsForSection = when (stepPermissionsForSection) {
					is PermissionLevel -> stepPermissionsForSection
					else -> currentStepPermissions.default
				}

				if (effectivePermissionsForSection == PermissionLevel.EDITABLE) {
					if (section is Section) {
						section.toValueContainers()
					} else {
						null
					}
				} else {
					null
				}
			}.filterNotNull().toMap()
		} else {
			null
		})
	}

	fun writeAs(current_user: String, values: String): String {
		val valuesObj: WriteTemplate = Engine.yamlMapper.readValue(values)

		println("persisting")
		println(valuesObj)

		val nextStep = appSpec.workflow.tasks.getOrNull(currentStepTaskIndex() + 1)
		if (nextStep != null) {
			currentStep = nextStep.name
			afterChangeStep()
		} else {
			lastStep()
		}

		return "ok"
	}

	fun lastStep() {
		println("app finised!")
	}
}

class Engine {
	val apps: MutableList<App> = mutableListOf()
	val app_instances: MutableList<AppInstance> = mutableListOf()
	val yamlMapper = Engine.yamlMapper

	fun registerApp(app: App) {
		apps.add(app)
	}

	companion object {
		val yamlMapper: ObjectMapper = {
			val mapper = ObjectMapper(YAMLFactory())
			mapper.registerModule(KotlinModule())
			mapper
		}()
	}

	fun newAppInstance(appIndex: Int, user: String): Int {
		if (apps.get(appIndex).canStart(user)) {
			val new_instance = apps.get(appIndex).create(user)
			app_instances.add(new_instance)
			return app_instances.indexOf(new_instance)
		} else {
			throw IllegalAccessError()
		}
	}
}


