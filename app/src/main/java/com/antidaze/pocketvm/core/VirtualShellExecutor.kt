package com.antidaze.pocketvm.core

import android.content.Context
import com.antidaze.pocketvm.model.VirtualInstance
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VirtualShellExecutor(
    private val context: Context,
    private val instance: VirtualInstance
) {
    private val vmManager = VirtualInstanceManager.get(context)
    private val rootDir = vmManager.getVmDir(instance.id)
    private val env = VirtualEnvironment(context, instance)
    private val pm = VirtualPackageManager.get(context, instance.id)

    private var currentDir: File = vmManager.getVmSubdir(instance.id, "sdcard")
    var isRootSession: Boolean = false

    val prompt: String
        get() {
            val user = if (isRootSession) "root" else "u0_a100"
            val symbol = if (isRootSession) "#" else "$"
            val relPath = currentDir.relativeToOrNull(rootDir)?.path ?: currentDir.name
            return "$user@pocketvm:/$relPath$symbol "
        }

    fun execute(commandLine: String): String {
        val trimmed = commandLine.trim()
        if (trimmed.isEmpty()) return ""

        val parts = trimmed.split("\\s+".toRegex())
        val cmd = parts[0]
        val args = parts.drop(1)

        return when (cmd) {
            "help" -> cmdHelp()
            "clear" -> ""
            "pwd" -> currentDir.absolutePath.replace(rootDir.absolutePath, "")
            "cd" -> cmdCd(args)
            "ls" -> cmdLs(args)
            "cat" -> cmdCat(args)
            "echo" -> args.joinToString(" ")
            "touch" -> cmdTouch(args)
            "mkdir" -> cmdMkdir(args)
            "rm" -> cmdRm(args)
            "cp" -> cmdCp(args)
            "mv" -> cmdMv(args)
            "id" -> if (isRootSession) "uid=0(root) gid=0(root) groups=0(root),1004(input),1007(log),1011(adb)" else "uid=10100(u0_a100) gid=10100(u0_a100) groups=10100(u0_a100),3003(inet)"
            "whoami" -> if (isRootSession) "root" else "u0_a100"
            "uname" -> if (args.contains("-a")) "Linux pocketvm 5.15.148-android-pocketvm+ #1 SMP PREEMPT aarch64 Android" else "Linux"
            "getprop" -> cmdGetProp(args)
            "setprop" -> cmdSetProp(args)
            "pm" -> cmdPm(args)
            "am" -> cmdAm(args)
            "su" -> cmdSu(args)
            "df" -> cmdDf()
            "free" -> cmdFree()
            "ps" -> cmdPs()
            "top" -> cmdTop()
            "date" -> SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.US).format(Date())
            else -> "pocketvm: $cmd: command not found (type 'help' for available commands)"
        }
    }

    private fun cmdHelp(): String {
        return """
            PocketVM Virtual Shell v2.0 (Built-in Toybox/Busybox emulator)
            Available commands:
              Navigation & Files: ls, cd, pwd, cat, touch, mkdir, rm, cp, mv
              Identity & System : whoami, id, uname, su, date, df, free, ps, top
              Android Tools     : getprop, setprop, pm, am
              Session           : clear, help
        """.trimIndent()
    }

    private fun cmdCd(args: List<String>): String {
        if (args.isEmpty() || args[0] == "~") {
            currentDir = vmManager.getVmSubdir(instance.id, "sdcard")
            return ""
        }
        val target = resolveFile(args[0])
        return if (target.isDirectory) {
            currentDir = target
            ""
        } else {
            "cd: no such file or directory: ${args[0]}"
        }
    }

    private fun cmdLs(args: List<String>): String {
        val showAll = args.any { it.contains("a") }
        val showLong = args.any { it.contains("l") }
        val targetDir = args.firstOrNull { !it.startsWith("-") }?.let { resolveFile(it) } ?: currentDir

        if (!targetDir.exists()) return "ls: ${targetDir.name}: No such file or directory"
        if (!targetDir.isDirectory) return targetDir.name

        val files = targetDir.listFiles() ?: return ""
        val sorted = files.sortedBy { it.name }

        return if (showLong) {
            val sb = StringBuilder("total ${sorted.size}\n")
            val sdf = SimpleDateFormat("MMM dd HH:mm", Locale.US)
            for (f in sorted) {
                if (!showAll && f.name.startsWith(".")) continue
                val type = if (f.isDirectory) "d" else "-"
                val perms = if (f.canWrite()) "rwxr-xr-x" else "r-xr-xr-x"
                val size = if (f.isDirectory) 4096 else f.length()
                val date = sdf.format(Date(f.lastModified()))
                sb.append(String.format("%s%s  1 %-8s %-8s %6d %s %s\n",
                    type, perms, if (isRootSession) "root" else "u0_a100",
                    if (isRootSession) "root" else "u0_a100", size, date, f.name))
            }
            sb.toString().trimEnd()
        } else {
            sorted.filter { showAll || !it.name.startsWith(".") }.joinToString("  ") { it.name }
        }
    }

    private fun cmdCat(args: List<String>): String {
        if (args.isEmpty()) return "cat: missing file operand"
        val f = resolveFile(args[0])
        return if (f.isFile) {
            try { f.readText() } catch (e: Exception) { "cat: ${e.message}" }
        } else {
            "cat: ${args[0]}: No such file or directory"
        }
    }

    private fun cmdTouch(args: List<String>): String {
        if (args.isEmpty()) return "touch: missing file operand"
        val f = resolveFile(args[0])
        f.createNewFile()
        return ""
    }

    private fun cmdMkdir(args: List<String>): String {
        if (args.isEmpty()) return "mkdir: missing operand"
        val f = resolveFile(args[0])
        f.mkdirs()
        return ""
    }

    private fun cmdRm(args: List<String>): String {
        if (args.isEmpty()) return "rm: missing operand"
        val isRecursive = args.any { it == "-r" || it == "-rf" }
        val path = args.firstOrNull { !it.startsWith("-") } ?: return "rm: missing operand"
        val f = resolveFile(path)
        return if (!f.exists()) {
            "rm: ${f.name}: No such file or directory"
        } else if (f.isDirectory && !isRecursive) {
            "rm: ${f.name}: is a directory (use -r to delete)"
        } else {
            f.deleteRecursively()
            ""
        }
    }

    private fun cmdCp(args: List<String>): String {
        if (args.size < 2) return "cp: missing destination file operand"
        val src = resolveFile(args[0])
        val dst = resolveFile(args[1])
        if (!src.exists()) return "cp: cannot stat '${args[0]}': No such file or directory"
        val target = if (dst.isDirectory) File(dst, src.name) else dst
        src.copyTo(target, overwrite = true)
        return ""
    }

    private fun cmdMv(args: List<String>): String {
        if (args.size < 2) return "mv: missing destination operand"
        val src = resolveFile(args[0])
        val dst = resolveFile(args[1])
        if (!src.exists()) return "mv: cannot stat '${args[0]}': No such file or directory"
        val target = if (dst.isDirectory) File(dst, src.name) else dst
        src.renameTo(target)
        return ""
    }

    private fun cmdGetProp(args: List<String>): String {
        return if (args.isEmpty()) {
            env.getAllProps().entries.joinToString("\n") { "[${it.key}]: [${it.value}]" }
        } else {
            env.getProp(args[0], "")
        }
    }

    private fun cmdSetProp(args: List<String>): String {
        if (args.size < 2) return "usage: setprop <key> <value>"
        if (!isRootSession && !env.isRootEnabled) return "setprop: permission denied (root required)"
        env.setProp(args[0], args.drop(1).joinToString(" "))
        return ""
    }

    private fun cmdPm(args: List<String>): String {
        if (args.isEmpty()) return "usage: pm list packages | pm install <apk> | pm uninstall <pkg>"
        return when (args[0]) {
            "list" -> {
                val apps = pm.getInstalledApps()
                apps.joinToString("\n") { "package:${it.packageName}" }
            }
            "install" -> {
                if (args.size < 2) return "usage: pm install <apk_path>"
                val apkFile = resolveFile(args[1])
                val res = pm.installApk(apkFile)
                if (res.isSuccess) "Success: installed ${res.getOrNull()?.packageName}" else "Failure [${res.exceptionOrNull()?.message}]"
            }
            "uninstall" -> {
                if (args.size < 2) return "usage: pm uninstall <package_name>"
                val ok = pm.uninstallApp(args[1])
                if (ok) "Success" else "Failure: package not found or cannot uninstall system app"
            }
            else -> "pm: unknown command '${args[0]}'"
        }
    }

    private fun cmdAm(args: List<String>): String {
        if (args.isEmpty()) return "usage: am start -n <package>"
        return "Starting: Intent { act=android.intent.action.MAIN pkg=${args.last()} }"
    }

    private fun cmdSu(args: List<String>): String {
        if (!env.isRootEnabled) {
            return "su: Permission denied (Virtual Root is disabled in VM Settings)"
        }
        isRootSession = !isRootSession
        return if (isRootSession) "Switched to virtual root session (uid=0)." else "Dropped root privileges."
    }

    private fun cmdDf(): String {
        val totalMb = instance.storageQuotaMb
        val usedMb = (instance.storageUsedBytes / (1024 * 1024)).coerceAtLeast(1)
        val freeMb = totalMb - usedMb
        return """
            Filesystem      Size    Used    Free   Use% Mounted on
            /dev/root       1.9G    720M    1.2G    38% /
            /dev/block/data ${totalMb}M   ${usedMb}M   ${freeMb}M    ${(usedMb * 100) / totalMb}% /data
            /dev/fuse       ${totalMb}M   ${usedMb}M   ${freeMb}M    ${(usedMb * 100) / totalMb}% /storage/emulated/0
        """.trimIndent()
    }

    private fun cmdFree(): String {
        val ram = instance.ramMb
        val used = (ram * 0.35).toInt()
        val free = ram - used
        return """
                   total        used        free      shared     buffers
            Mem: ${ram}M     ${used}M     ${free}M          0M         24M
            Swap:    0M          0M          0M
        """.trimIndent()
    }

    private fun cmdPs(): String {
        return """
            USER      PID   PPID  VSIZE  RSS   WCHAN            PC  NAME
            root        1      0  12.4M 3.2M  ep_poll  0000000000 S init
            root        2      0      0    0  kthreadd 0000000000 S [kthreadd]
            system    420      1  84.2M  24M  ep_poll  0000000000 S servicemanager
            system    512      1 142.1M  48M  ep_poll  0000000000 S surfaceflinger
            root      640      1 320.4M  96M  poll     0000000000 S zygote64
            system    800    640   1.2G 180M  ep_poll  0000000000 S system_server
            u0_a100  1024    640 450.2M  64M  ep_poll  0000000000 S com.pocketvm.launcher
        """.trimIndent()
    }

    private fun cmdTop(): String {
        return """
            Tasks: 42 total, 1 running, 41 sleeping, 0 stopped
            %Cpu(s): 2.4 us, 1.1 sy, 0.0 ni, 96.5 id, 0.0 wa
            PID USER      PR  NI    VIRT    RES    SHR S  %CPU  %MEM     TIME+ COMMAND
            800 system    20   0    1.2G   180M    32M S   2.1   8.8   0:14.21 system_server
            512 system   -10  -4  142.1M    48M    16M S   1.4   2.3   0:08.45 surfaceflinger
           1024 u0_a100   20   0  450.2M    64M    24M S   0.8   3.1   0:05.12 com.pocketvm.launcher
        """.trimIndent()
    }

    private fun resolveFile(path: String): File {
        return when {
            path.startsWith("/") -> File(rootDir, path.removePrefix("/"))
            path.startsWith("~") -> File(vmManager.getVmSubdir(instance.id, "sdcard"), path.removePrefix("~").removePrefix("/"))
            else -> File(currentDir, path)
        }
    }
}
