package io.compgen.sqz.cli;

import io.compgen.cmdline.Help;
import io.compgen.cmdline.License;
import io.compgen.cmdline.MainBuilder;

public class SQZMain {
	
	public static void main(String[] args) throws Exception {
        try {
            new MainBuilder()
                .setProgName("sqz")
                .setHelpHeader("SQZ - Secure sequencing read archiver\n---------------------------------------")
                .setDefaultUsage("Usage: sqz cmd [options]")
                .setHelpFooter("http://compgen.io/sqz\n"+MainBuilder.readFile("VERSION"))
                .setCategoryOrder(new String[] { "sqz", "help"})
                .addCommand(License.class)
                .addCommand(Help.class)
                .addCommand(FastqToSqz.class)
                .addCommand(SqzToFastq.class)
                .addCommand(SqzText.class)
                .addCommand(SqzVerify.class)
                .findAndRun(args);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
	}
}
