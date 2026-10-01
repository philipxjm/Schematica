package com.github.lunatrius.schematica.compat.microblock;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import net.minecraft.client.Minecraft;

import com.github.lunatrius.schematica.reference.Reference;

/**
 * Microblock printing log: every click, self-check result and misplacement, in its own file
 * (logs/schematica-microblocks.log) so it stays out of the FML client log.
 */
public final class MicroblockLog {

    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss.SSS");
    private static PrintWriter out;
    private static boolean failed;

    private MicroblockLog() {}

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
                "schematica-microblocks.log");
            try {
                file.getParentFile()
                    .mkdirs();
                out = new PrintWriter(new FileWriter(file, true), true);
                out.println("=== " + new Date() + " ===");
            } catch (IOException e) {
                failed = true;
                Reference.logger.warn("Could not open {}; microblock printing will not be logged", file, e);
            }
        }
        return out;
    }
}
