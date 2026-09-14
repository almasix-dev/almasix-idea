package com.almasix.ide

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * Interactive ``make:model`` dialog — name + companion flags matching smith.
 * Thin UI shell; flag formatting lives in [AlmasixMakeCatalog.modelSmithArgs].
 */
class AlmasixModelMakeDialog(
    project: Project,
    initialName: String = "",
) : DialogWrapper(project) {
    private val nameField = JTextField(initialName, 28)
    private val allBox = JCheckBox("All companions (-a)")
    private val migrationBox = JCheckBox("Migration (-m)")
    private val factoryBox = JCheckBox("Factory (-f)")
    private val seedBox = JCheckBox("Seeder (-s)")
    private val controllerBox = JCheckBox("Controller (-c)")
    private val resourceBox = JCheckBox("Resource controller (-r)")
    private val apiBox = JCheckBox("API resource controller (--api)")
    private val policyBox = JCheckBox("Policy (--policy)")
    private val requestsBox = JCheckBox("Form requests (-R)")

    init {
        title = "New Almasix Model"
        allBox.addActionListener {
            val on = allBox.isSelected
            listOf(
                migrationBox, factoryBox, seedBox, controllerBox,
                resourceBox, apiBox, policyBox, requestsBox,
            ).forEach {
                it.isSelected = on
                it.isEnabled = !on
            }
        }
        init()
    }

    override fun createCenterPanel(): JComponent {
        val form = JPanel(BorderLayout(0, 8))
        val nameRow = JPanel(BorderLayout(8, 0))
        nameRow.add(JLabel("Model name:"), BorderLayout.WEST)
        nameRow.add(nameField, BorderLayout.CENTER)
        val boxes = JPanel(GridLayout(0, 1, 0, 2))
        boxes.add(JLabel("Also generate:"))
        boxes.add(allBox)
        boxes.add(migrationBox)
        boxes.add(factoryBox)
        boxes.add(seedBox)
        boxes.add(controllerBox)
        boxes.add(resourceBox)
        boxes.add(apiBox)
        boxes.add(policyBox)
        boxes.add(requestsBox)
        form.add(nameRow, BorderLayout.NORTH)
        form.add(boxes, BorderLayout.CENTER)
        return form
    }

    fun modelName(): String = nameField.text.trim()

    fun options(): AlmasixMakeCatalog.ModelOptions = AlmasixMakeCatalog.ModelOptions(
        all = allBox.isSelected,
        migration = migrationBox.isSelected,
        factory = factoryBox.isSelected,
        seed = seedBox.isSelected,
        controller = controllerBox.isSelected,
        resource = resourceBox.isSelected,
        api = apiBox.isSelected,
        policy = policyBox.isSelected,
        requests = requestsBox.isSelected,
    )

    companion object {
        /** Show the dialog; null if cancelled / blank name. */
        fun prompt(project: Project): Pair<String, AlmasixMakeCatalog.ModelOptions>? {
            val dialog = AlmasixModelMakeDialog(project)
            if (!dialog.showAndGet()) return null
            val name = dialog.modelName()
            if (name.isEmpty()) return null
            return name to dialog.options()
        }
    }
}
