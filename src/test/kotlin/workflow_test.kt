import com.fasterxml.jackson.module.kotlin.readValue
import main.*
import org.junit.jupiter.api.Assertions.assertEquals

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

import java.io.File

class Calculator {
	fun add(a: Int, b: Int): Int {
		return a + b
	}
}

class Tests {
	val onboardingApp = App(
		name = "Employee Onboarding",
		description = "Initiate the process to onboard a new employee",
		form = Form(listOf(
			Section(
				name = "Employee Onboarding",
				fields = listOf(
					TextInputField(
						label = "Employee's Name"
					),
					UserInputField(
						label = "Reporting Manager"),
					BooleanInputField(id = "external_email", label = "New Person External Email")
				)
			),
			Section(name = "Employee Personal Details"),
			Section(name = "Employee Bank Details"),
			Section(name = "Document Proofs"),
			Section(name = "Reporting Manager's Update",
				fields = listOf(
					BooleanInputField(label = "IT Assets Required?"),
					BooleanInputField(label = "Other Assets Required?")
				)),
			Section(name = "IT Assets to be allocated"),
			Section(name = "Other Assets to be allocated"),
			Section(name = "IT Assets Allocated"),
			Section(name = "Other Assets Allocated"),
			Section(name = "HR Comments")
		)),
		workflow = Workflow(
			tasks = listOf(
				StartTask(
					whoCanStartIt = "*@test.com",
					permissions = Permissions(
						default = PermissionLevel.HIDDEN,
						sectionPermissions = mapOf(
							"Employee Onboarding" to PermissionLevel.EDITABLE
						)
					)
				),
				InputTask(
					name = "Update Employee Information",
					allowedInputer = listOf("req.initiator"),
					permissions = Permissions(
						default = PermissionLevel.HIDDEN,
						sectionPermissions = mapOf(
							"Employee Onboarding" to PermissionLevel.READONLY,
							"Employee Personal Details" to PermissionLevel.EDITABLE,
							"Employee Bank Details" to PermissionLevel.EDITABLE,
							"Document Proofs" to PermissionLevel.EDITABLE
						)
					)
				),
				ApprovalTask(
					name = "Approve",
					allowedApprovers = listOf("form.reporting manager"),
					permissions = Permissions(
						default = PermissionLevel.HIDDEN,
						sectionPermissions = mapOf(
							"Employee Onboarding" to PermissionLevel.READONLY,
							"Employee Personal Details" to PermissionLevel.READONLY,
							"Document Proofs" to PermissionLevel.READONLY,
							"Reporting Manager's Update" to PermissionLevel.EDITABLE,
							"IT Assets to be allocated" to PermissionLevel.EDITABLE,
							"Other Assets to be allocated" to PermissionLevel.EDITABLE
						)
					)),
				ParallelBranchesTask(
					branches = listOf(
						Branch(
							name = "IT Asset Allocation",
							happens = "IT_Assets_Required=1",
							tasks = listOf(
								InputTask(
									name = "Allocate IT Assets",
									allowedInputer = listOf("req.initiator"),
									permissions = Permissions(
										default = PermissionLevel.HIDDEN,
										sectionPermissions = mapOf(
											"Employee Onboarding" to PermissionLevel.READONLY,
											"Reporting Manager's Update" to PermissionLevel.READONLY,
											"IT Assets to be allocated" to PermissionLevel.READONLY,
											"IT Assets Allocated" to PermissionLevel.EDITABLE
										)
									)
								)
							)
						),
						Branch(
							name = "Other Assets Allocation",
							happens = "Other_Assets_Required=1",
							tasks = listOf(
								InputTask(
									name = "Allocate Other Assets",
									allowedInputer = listOf("req.initiator"),
									permissions = Permissions(
										default = PermissionLevel.HIDDEN,
										sectionPermissions = mapOf(
											"Employee Onboarding" to PermissionLevel.READONLY,
											"Reporting Manager's Update" to PermissionLevel.READONLY,
											"Other Assets to be allocated" to PermissionLevel.READONLY,
											"Other Assets Allocated" to PermissionLevel.EDITABLE
										)
									)
								)
							)
						)
					)
				),
				ApprovalTask(
					name = "Reporting Manger's Review",
					allowedApprovers = listOf("form.reporting manager"),
					permissions = Permissions(
						PermissionLevel.READONLY,
						sectionPermissions = mapOf(
							"HR Comments" to PermissionLevel.HIDDEN
						)
					)
				),
				InputTask(
					name = "HR Review",
					allowedInputer = listOf("req.initiator"),
					permissions = Permissions(
						PermissionLevel.READONLY,
						sectionPermissions = mapOf(
							"HR Comments" to PermissionLevel.EDITABLE
						)
					)
				)
			)
		)
	)

	@Test
	fun testDeserialization() {
		val engine = Engine()

		val appOnDiskString = File("onboarding_app.yaml").bufferedReader().readLines().joinToString("\n")

		val appOnDisk: App = engine.yamlMapper.readValue(appOnDiskString)
		assertEquals(onboardingApp, appOnDisk)
	}

	@Test
	fun testUserViews() {
		val engine = Engine()

		val appOnDiskString = File("onboarding_app.yaml").bufferedReader().readLines().joinToString("\n")
		val appOnDisk: App = engine.yamlMapper.readValue(appOnDiskString)

		engine.registerApp(appOnDisk)

		val current_user = "ian@test.com"

		val onboarding_request_id = engine.newAppInstance(0, current_user)

		println(engine.app_instances[onboarding_request_id].currentStepTask())

		println(engine.app_instances[onboarding_request_id].viewAs(current_user))
		println(engine.yamlMapper.writeValueAsString(engine.app_instances[onboarding_request_id].writeTemplate(current_user)))

		println(".")
	}

	@ParameterizedTest(name = "{0} + {1} = {2}")
	@CsvSource(
		"0,    1,   1",
		"1,    2,   3",
		"49,  51, 100",
		"1,  100, 101"
	)
	fun add(first: Int, second: Int, expectedResult: Int) {
		val calculator = Calculator()
		assertEquals(expectedResult, calculator.add(first, second)) {
			"$first + $second should equal $expectedResult"
		}
	}

}