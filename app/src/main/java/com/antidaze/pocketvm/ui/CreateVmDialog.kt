package com.antidaze.pocketvm.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.antidaze.pocketvm.R
import com.antidaze.pocketvm.core.VirtualInstanceManager
import com.antidaze.pocketvm.model.*
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText

class CreateVmDialog(
    private val onCreated: (VirtualInstance) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_create_vm, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val editName = view.findViewById<TextInputEditText>(R.id.edit_vm_name)
        val chipGroupPresets = view.findViewById<ChipGroup>(R.id.chip_group_presets)
        val chipGroupRam = view.findViewById<ChipGroup>(R.id.chip_group_ram)
        val chipGroupDisplay = view.findViewById<ChipGroup>(R.id.chip_group_display)
        val switchRoot = view.findViewById<SwitchMaterial>(R.id.switch_root)
        val btnCreate = view.findViewById<MaterialButton>(R.id.btn_create)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btn_cancel)

        btnCancel.setOnClickListener { dismiss() }

        // Adjust settings based on preset choice
        chipGroupPresets.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                R.id.preset_pixel8 -> {
                    editName.setText("Google Pixel 8 Pro")
                    switchRoot.isChecked = false
                }
                R.id.preset_s24 -> {
                    editName.setText("Samsung Galaxy S24")
                    switchRoot.isChecked = false
                }
                R.id.preset_gaming -> {
                    editName.setText("Gaming Lab (120Hz)")
                    switchRoot.isChecked = true
                    view.findViewById<Chip>(R.id.ram_4gb)?.isChecked = true
                }
                R.id.preset_privacy -> {
                    editName.setText("Privacy Sandbox")
                    switchRoot.isChecked = false
                }
            }
        }

        btnCreate.setOnClickListener {
            val name = editName.text?.toString()?.trim().takeIf { !it.isNullOrBlank() } ?: "Android 14 VM"

            // Device profile & OS profile
            val (devProfile, osProfile) = when (chipGroupPresets.checkedChipId) {
                R.id.preset_s24 -> Pair(DeviceProfile.galaxyS24(), AndroidVersionProfile.android14())
                R.id.preset_gaming -> Pair(DeviceProfile.rogPhone8(), AndroidVersionProfile.android13())
                R.id.preset_privacy -> Pair(DeviceProfile.cleanAosp(), AndroidVersionProfile.android14())
                else -> Pair(DeviceProfile.pixel8Pro(), AndroidVersionProfile.android14())
            }

            // RAM
            val ramMb = when (chipGroupRam.checkedChipId) {
                R.id.ram_1gb -> 1024
                R.id.ram_3gb -> 3072
                R.id.ram_4gb -> 4096
                else -> 2048
            }

            // Display
            val displayConfig = when (chipGroupDisplay.checkedChipId) {
                R.id.disp_1080x1920 -> DisplayConfig.fhd400()
                R.id.disp_720x1280 -> DisplayConfig.hd320()
                else -> if (chipGroupPresets.checkedChipId == R.id.preset_gaming) DisplayConfig.gaming120Hz() else DisplayConfig.fhdPlus420()
            }

            val rootConfig = RootConfig(isRootEnabled = switchRoot.isChecked)

            val newInstance = VirtualInstance(
                name = name,
                ramMb = ramMb,
                deviceProfile = devProfile,
                osProfile = osProfile,
                displayConfig = displayConfig,
                rootConfig = rootConfig
            )

            val created = VirtualInstanceManager.get(requireContext()).createInstance(newInstance)
            Toast.makeText(requireContext(), getString(R.string.toast_vm_created, name), Toast.LENGTH_SHORT).show()
            onCreated(created)
            dismiss()
        }
    }

    companion object {
        const val TAG = "CreateVmDialog"
    }
}
