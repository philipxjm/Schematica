package com.github.lunatrius.schematica.compat.architecture;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import net.minecraft.client.Minecraft;

import com.github.lunatrius.schematica.reference.Reference;

/**
 * ArchitectureCraft printing log: every click, fix-up and give-up, in its own file
 * (logs/schematica-architecture.log) so it stays out of the FML client log.
 */
public final class ArchitectureLog {

    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss.SSS");
    private static PrintWriter out;
    private static boolean failed;

    private ArchitectureLog() {}

    public static synchronized void log(String format, Object... args) {
        final PrintWriter writer = writer();
        if (writer != null) {
            writer.println("[" + TIME.format(new Date()) + "] " + String.format(format, args));
        }
    }

    private static PrintWriter writer() {
        if (out == null && !failed) {
            final File file = new File(
                new File(Minecraft.getMinecraft().mcDataDir, "logs"),
                "schematica-architecture.log");
            try {
                file.getParentFile()
                    .mkdirs();
                out = new PrintWriter(new FileWriter(file, true), true);
                out.println("=== " + new Date() + " ===");
            } catch (IOException e) {
                failed = true;
                Reference.logger.warn("Could not open {}; ArchitectureCraft printing will not be logged", file, e);
            }
        }
        return out;
    }
}
