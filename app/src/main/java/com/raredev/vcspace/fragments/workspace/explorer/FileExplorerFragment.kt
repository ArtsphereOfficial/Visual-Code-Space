/**
 * This file is part of Visual Code Space.
 *
 * Visual Code Space is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Visual Code Space is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Visual Code Space.
 * If not, see <https://www.gnu.org/licenses/>.
 */
package com.raredev.vcspace.fragments.workspace.explorer

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.FileUtils
import com.blankj.utilcode.util.SizeUtils.dp2px
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.raredev.vcspace.activities.editor.EditorActivity
import com.raredev.vcspace.adapters.FileListAdapter
import com.raredev.vcspace.databinding.FragmentFileExplorerBinding
import com.raredev.vcspace.events.OnDeleteFileEvent
import com.raredev.vcspace.events.OnRenameFileEvent
import com.raredev.vcspace.extensions.launchWithProgressDialog
import com.raredev.vcspace.fragments.sheets.OptionsListBottomSheet
import com.raredev.vcspace.models.SheetOptionItem
import com.raredev.vcspace.res.R
import com.raredev.vcspace.res.databinding.LayoutTextinputBinding
import com.raredev.vcspace.utils.ApkInstaller
import com.raredev.vcspace.utils.FileUtil
import com.raredev.vcspace.utils.showShortToast
import com.raredev.vcspace.viewmodel.FileExplorerViewModel
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus

class FileExplorerFragment : Fragment(), FileListAdapter.OnFileClickListener {

  private val viewModel by viewModels<FileExplorerViewModel>(ownerProducer = { requireActivity() })

  private var _binding: FragmentFileExplorerBinding? = null
  private val binding: FragmentFileExplorerBinding
    get() = checkNotNull(_binding)

  private val coroutineScope = CoroutineScope(Dispatchers.Default)
  private val adapter by lazy { FileListAdapter(this) }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = FragmentFileExplorerBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    viewModel.files.observe(viewLifecycleOwner) { files ->
      binding.emptyFolder.isVisible = files.isEmpty()
      adapter.submitList(files)
    }

    viewModel.currentPath.observe(viewLifecycleOwner) { path -> binding.pathList.setPath(path) }
    binding.pathList.setFileExplorerViewModel(viewModel)

    binding.navigationSpace.addItem(R.string.refresh, R.drawable.ic_refresh) { refreshFiles() }
    binding.navigationSpace.addItem(R.string.create, R.drawable.ic_add) {
      showCreateFileDialog(viewModel.currentPath.value!!)
    }

    binding.rvFiles.layoutManager = LinearLayoutManager(requireContext())
    binding.rvFiles.adapter = adapter
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }

  override fun onStart() {
    super.onStart()
    refreshFiles()
  }

  override fun onFileClickListener(file: File) {
    if (file.isDirectory) {
      setCurrentPath(file.absolutePath)
    } else {
      if (FileUtil.isValidTextFile(file.name)) {
        (requireActivity() as EditorActivity).openFile(file)
      } else {
        if (file.name.endsWith(".apk")) {
          ApkInstaller.installApplication(requireContext(), file)
        }
      }
    }
  }

  override fun onFileLongClickListener(file: File, view: View): Boolean {
    val sheet = OptionsListBottomSheet()
    sheet.addOption(SheetOptionItem(R.drawable.ic_copy, getString(R.string.copy_path)))
    sheet.addOption(SheetOptionItem(R.drawable.ic_file_rename, getString(R.string.rename)))
    sheet.addOption(SheetOptionItem(R.drawable.ic_delete, getString(R.string.delete)))

    sheet.setOptionClickListener { option ->
      when (option.name) {
        getString(R.string.copy_path) -> ClipboardUtils.copyText(file.absolutePath)
        getString(R.string.rename) -> showRenameFileDialog(file)
        getString(R.string.delete) -> showDeleteFileDialog(file)
      }
      sheet.dismiss()
    }
    sheet.show(childFragmentManager, null)

    return true
  }

  fun setCurrentPath(path: String) {
    viewModel.setCurrentPath(path)
  }

  fun refreshFiles() {
    viewModel.refreshFiles()
  }

  @SuppressLint("RestrictedApi")
  @Suppress("DEPRECATION")
  private fun showCreateFileDialog(path: String) {
    MaterialAlertDialogBuilder(requireContext()).apply {
      val binding = LayoutTextinputBinding.inflate(LayoutInflater.from(requireContext()))
      setView(binding.root, dp2px(20f), dp2px(5f), dp2px(20f), 0)
      setTitle(R.string.create)
      setNeutralButton(R.string.cancel, null)
      setNegativeButton(R.string.file) { _, _ ->
        val name = binding.inputEdittext.text.toString().trim()
        with(File(path, name)) {
          try {
            if (!exists() && createNewFile()) {
              viewModel.refreshFiles()
            }
          } catch (ioe: IOException) {
            ioe.printStackTrace()
          }
        }
      }
      setPositiveButton(R.string.folder) { _, _ ->
        val name = binding.inputEdittext.text.toString().trim()
        with(File(path, name)) {
          try {
            if (!exists() && mkdirs()) {
              viewModel.refreshFiles()
            }
          } catch (ioe: IOException) {
            ioe.printStackTrace()
          }
        }
      }
      val dialog = create()
      dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
      dialog.setOnShowListener {
        binding.inputLayout.setHint(R.string.file_name_hint)
        binding.inputEdittext.requestFocus()
      }
      dialog.show()
    }
  }

  @SuppressLint("RestrictedApi")
  @Suppress("DEPRECATION")
  private fun showRenameFileDialog(file: File) {
    MaterialAlertDialogBuilder(requireContext()).apply {
      val binding = LayoutTextinputBinding.inflate(LayoutInflater.from(requireContext()))
      setView(binding.root, dp2px(20f), dp2px(5f), dp2px(20f), 0)
      setTitle(R.string.rename)
      setNegativeButton(R.string.cancel, null)
      setPositiveButton(R.string.rename) { _, _ ->
        coroutineScope.launchWithProgressDialog(
          configureBuilder = { builder ->
            builder.setMessage(R.string.please_wait)
            builder.setCancelable(false)
          },
          action = { _ ->
            val name = binding.inputEdittext.text.toString().trim()
            val newFile = File(file.parentFile, name)
            val renamed = file.renameTo(newFile)

            if (!renamed) {
              return@launchWithProgressDialog
            }

            EventBus.getDefault().post(OnRenameFileEvent(file, newFile))

            withContext(Dispatchers.Main) {
              showShortToast(requireContext(), getString(R.string.renamed_message))
              viewModel.refreshFiles()
            }
          }
        )
      }
      val dialog = create()
      dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
      dialog.setOnShowListener {
        binding.inputLayout.setHint(R.string.rename_hint)
        binding.inputEdittext.setText(file.name)
        binding.inputEdittext.requestFocus()
      }
      dialog.show()
    }
  }

  private fun showDeleteFileDialog(file: File) {
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.delete)
      .setMessage(getString(R.string.delete_message, file.name))
      .setNegativeButton(R.string.no, null)
      .setPositiveButton(R.string.delete) { _, _ ->
        coroutineScope.launchWithProgressDialog(
          configureBuilder = { builder ->
            builder.setMessage(R.string.please_wait)
            builder.setCancelable(false)
          },
          action = { _ ->
            val deleted = FileUtils.delete(file)

            if (!deleted) {
              return@launchWithProgressDialog
            }

            EventBus.getDefault().post(OnDeleteFileEvent(file))

            withContext(Dispatchers.Main) {
              showShortToast(requireContext(), getString(R.string.deleted_message))
              viewModel.refreshFiles()
            }
          }
        )
      }
      .show()
  }
}
