package main

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import kotlin.reflect.KClass


@JsonTypeInfo(
	use = JsonTypeInfo.Id.NAME
)
@JsonSubTypes(
	JsonSubTypes.Type(value = TextInputField::class, name = "text"),
	JsonSubTypes.Type(value = UserInputField::class, name = "user"),
	JsonSubTypes.Type(value = BooleanInputField::class, name = "yesno")
)
interface InputField<T : Any> {
	val id: String
		get() = Helper.computerize(label)

	abstract val label: String
	abstract val required: Boolean
}

@JsonTypeInfo(
	use = JsonTypeInfo.Id.NAME
)
@JsonSubTypes(
	JsonSubTypes.Type(value = TextValueContainer::class, name = "text"),
	JsonSubTypes.Type(value = BooleanValueContainer::class, name = "bool")
)
abstract class ValueContainer<T : Any> {
	abstract val id: String
	abstract var value: T
	abstract val valueType: KClass<T>
}

data class BooleanInputField(
	override val label: String,
	override val id: String = Helper.computerize(label),
	override val required: Boolean = true
) : InputField<Boolean> {
}

data class UserInputField(
	override val label: String,
	override val id: String = Helper.computerize(label),
	override val required: Boolean = true
) : InputField<String> {
}

object Helper {
	fun computerize(s: String) = s.replace("[^\\p{IsAlphabetic}^\\p{IsDigit}]".toRegex(), "_")
}

data class TextValueContainer(
	override val id: String,
	override var value: String = "some text here"
) : ValueContainer<String>() {
	@JsonIgnore
	override val valueType: KClass<String> = String::class
}

data class BooleanValueContainer(
	override val id: String,
	override var value: Boolean = false
) : ValueContainer<Boolean>() {
	@JsonIgnore
	override val valueType: KClass<Boolean> = Boolean::class
}

data class TextInputField(
	override val label: String,
	override val required: Boolean = true,
	override val id: String = Helper.computerize(label)
) : InputField<String> {
}


object ValueContainers {
	fun fromInputField(field: InputField<*>): ValueContainer<*> {
		return when(field) {
			is TextInputField -> TextValueContainer(field.id)
			is BooleanInputField -> BooleanValueContainer(field.id)
			is UserInputField -> TextValueContainer(field.id)
			else -> TextValueContainer(field.id)
		}
	}
}
@JsonTypeInfo(
	use = JsonTypeInfo.Id.NAME
)
@JsonSubTypes(
	JsonSubTypes.Type(value = Section::class, name = "section")
// Table
)
abstract class SectionOrTable {
	abstract val name: String
}

data class Section(
	override val name: String = "untitled Section",
	val helpText: String? = "",
	val fields: List<InputField<*>> = listOf()
) : SectionOrTable() {
	fun fieldsAsMap(): Map<String, InputField<*>> = fields.map { (it.id to it) }.toMap()

	fun toValueContainers() = (this.name to this.fields.map(ValueContainers::fromInputField))
}

data class Form(
	val sections: List<Section>
) {
	fun sectionNames(): List<String> = sections.map { it.name }

	fun toValueContainers() = this.sections.map(Section::toValueContainers).toMap()
}

enum class AdvancedAllowedApprover {
	NOBODY,
	INITIATOR,
	INITIATORS_MANAGER,
	INITIATORS_DEPARTMENT_HEAD,
	INITIATORS_LOCATION_HEAD
}

abstract class TaskWithAssignee : Task() {
	abstract fun assignees(): List<String>
}
data class ApprovalTask(
	override val name: String,
	val allowedApprovers: List<String>,
	val advancedAllowedApprover: AdvancedAllowedApprover = AdvancedAllowedApprover.NOBODY,
	override val permissions: Permissions
) : TaskWithAssignee() {
	override fun assignees(): List<String> = allowedApprovers
}

data class InputTask(
	override val name: String,
	val allowedInputer: List<String>,
	val advancedAllowedInputer: AdvancedAllowedApprover = AdvancedAllowedApprover.NOBODY,
	override val permissions: Permissions
) : TaskWithAssignee() {
	override fun assignees(): List<String> = allowedInputer
}

typealias Logic = String

data class Branch(
	val name: String = "Untitled Branch",
	val happens: Logic = "TRUE",
	val tasks: List<Task>
)

data class ParallelBranchesTask(
	val branches: List<Branch>
) : Task() {
	override val name: String
		get() = "permission_branch"
	override val permissions: Permissions
		get() = Permissions(PermissionLevel.READONLY)
}

data class StartTask(
	val whoCanStartIt: String,
	override val permissions: Permissions
) : Task() {
	override val name: String
		get() = "start"
}

data class GotoTask(
	val step_name: String,
	val condition: Logic = "TRUE",
	override val permissions: Permissions = Permissions(
		PermissionLevel.READONLY
	), override val name: String = "goto_"
) : Task()

@JsonTypeInfo(
	use = JsonTypeInfo.Id.NAME
)
@JsonSubTypes(
	JsonSubTypes.Type(value = ParallelBranchesTask::class, name = "parallel"),
	JsonSubTypes.Type(value = GotoTask::class, name = "goto"),
	JsonSubTypes.Type(value = InputTask::class, name = "input"),
	JsonSubTypes.Type(value = ApprovalTask::class, name = "approval"),
	JsonSubTypes.Type(value = StartTask::class, name = "start")

	/*
	 Send an email
	 Start a new time
	 Start multiple items from Table
	 Update an Item
	 Create or Update a Master Record
	 Send Data to a Webhook
	 */
)
abstract class Task {
	abstract val name: String
	abstract val permissions: Permissions
}

data class Workflow(
	val tasks: List<Task>
) {
	init {
		assert(tasks.first() is StartTask && tasks.drop(1).all { it !is StartTask }) {
			"workflow must contain one, and only one, StartTask at the beginning"
		}
	}
}

enum class PermissionLevel {
	HIDDEN,
	READONLY,
	EDITABLE
}

data class Permissions(
	val default: PermissionLevel,
	val sectionPermissions: Map<String, PermissionLevel> = mapOf()
)

data class App(
	val name: String,
	val description: String? = null,
	val form: Form,
	val workflow: Workflow
) {

	fun create(user: String): AppInstance {
		return AppInstance(this, user)
	}

	fun startTask(): StartTask = workflow.tasks[0] as StartTask

	fun canStart(user: String): Boolean {
		return true
	}

	fun validate() {
		val sections = form.sectionNames()
		workflow.tasks.forEach { task ->
			task.permissions.sectionPermissions.forEach { sectionName, _ ->
				assert(sections.contains(sectionName)) {
					"unknown $sectionName in $task"
				}
			}
		}
	}

	init {
		validate()
	}
}