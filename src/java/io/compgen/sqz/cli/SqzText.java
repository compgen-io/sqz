package io.compgen.sqz.cli;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractCommand;
import io.compgen.common.StringUtils;
import io.compgen.sqz.SQZReader;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.security.GeneralSecurityException;

@Command(name="text", desc="Extract text annotation from SQZ file.", category="sqz", experimental=true)
public class SqzText extends AbstractCommand {
    
    private String filename = null;
    private String password = null;
    private String passwordFile = null;
    private boolean listOnly = false;

    private String textName = null;

    @UnnamedArg(name = "INFILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Option(desc = "Just list names of the annotation blocks", name = "list")
    public void setList(boolean val) {
        this.listOnly = val;
    }
    
    @Option(desc = "Name of the text annotation to fetch (default: all)", name = "name")
    public void setTextName(String textName) {
        this.textName = textName;
    }
    
    @Option(desc = "Decryption password", name = "pass")
    public void setPassword(String password) {
        this.password = password;
    }
    
    @Option(desc = "File containing decryption password", name = "pass-file")
    public void setPasswordFile(String passwordFile) {
        this.passwordFile = passwordFile;
    }

    @Exec
    public void exec() throws CommandArgumentException, IOException, GeneralSecurityException  {        
        if (filename == null) {
            throw new CommandArgumentException("You must specify an input SQZ file!");
        }
        if (password == null && passwordFile != null) {
            password = StringUtils.strip(new BufferedReader(new FileReader(passwordFile)).readLine());
        }

        SQZReader reader;
        if (filename.equals("-")) {
            reader = SQZReader.open(System.in, false, password, verbose);
            if (verbose) {
                System.err.println("Input: stdin");
            }
        } else {
            reader = SQZReader.open(filename, false, password, verbose);
            if (verbose) {
                System.err.println("Input: " + filename);
            }
        }
        
        reader.fetchText();
        reader.close();
        
        if (reader.getException() != null) {
            System.err.println(reader.getException().getMessage());
            System.err.println((filename.equals("-") ? "stdin": filename) + " is not valid!");
            System.exit(1);
        }

        if (reader.getTextNames().size() > 0) {
            if (listOnly) {
                for (String name: reader.getTextNames()) {
                    System.out.println(name);
                }
            } else if (textName == null) {
                for (String name: reader.getTextNames()) {
                    System.out.println("["+name+"]");
                    System.out.println(reader.getText(name));
                }
            } else {
                System.out.println(reader.getText(textName));
            }
        }
    }    
}
