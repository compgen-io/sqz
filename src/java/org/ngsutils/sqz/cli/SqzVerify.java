package org.ngsutils.sqz.cli;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Date;
import java.util.Iterator;

import org.ngsutils.NGSUtilsException;
import org.ngsutils.fastq.FastqRead;
import org.ngsutils.sqz.SQZReader;
import org.ngsutils.support.StringUtils;
import org.ngsutils.support.cli.AbstractCommand;
import org.ngsutils.support.cli.Command;

import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.CommandLineInterface;
import com.lexicalscope.jewel.cli.Option;
import com.lexicalscope.jewel.cli.Unparsed;

@CommandLineInterface(application="sqz verify")
@Command(name="verify", desc="Verify that the SQZ file is valid.", cat="sqz", experimental=true)
public class SqzVerify extends AbstractCommand {
    
    private String filename = null;
    private String password = null;
    private String passwordFile = null;

    @Unparsed(name = "INFILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Option(description = "Decryption password", longName = "pass", defaultToNull=true)
    public void setPassword(String password) {
        this.password = password;
    }
    
    @Option(description = "File containing decryption password", longName = "pass-file", defaultToNull=true)
    public void setPasswordFile(String passwordFile) {
        this.passwordFile = passwordFile;
    }

    @Override
    public void exec() throws NGSUtilsException {        
        try {
            if (filename == null) {
                throw new ArgumentValidationException("You must specify an input FQA file!");
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
            
            if (verbose) {
                System.err.println("SQZ version: "+reader.getHeader().major+"."+reader.getHeader().minor);
                System.err.println("Encrypted: "+(reader.getHeader().encryption == null ? "no" : reader.getHeader().encryption));
                System.err.println("Includes comments: "+(reader.getHeader().hasComments ? "yes" : "no"));
                System.err.println("Space: "+(reader.getHeader().colorspace ? "color" : "base")+"-space");
                switch (reader.getHeader().compressionType) {
                case 0:
                    System.err.println("Compression: none");
                    break;
                case 1:
                    System.err.println("Compression: deflate");
                    break;
                case 2:
                    System.err.println("Compression: bzip2");
                    break;
                default:
                    System.err.println("Compression: unknown!");
                    break;
                }
                System.err.println("Reads per fragment: " + reader.getHeader().seqCount);
                System.err.println("Date created: " + new Date(reader.getHeader().timestamp*1000));
            }
            
            long i=0;
    
            Iterator<FastqRead>it = reader.iterator();
            while (it.hasNext()) {
                if (verbose) {
                    i++;
                    if (i % 100000 == 0) {
                        System.err.println("Read: " + i);
                    }
                    
                }
                it.next();                
            }
    
            reader.close();
            
            if (reader.getException() != null) {
                System.err.println(reader.getException().getMessage());
                System.err.println((filename.equals("-") ? "stdin": filename) + " is not valid!");
                System.exit(1);
            }
    
            if (verbose) {
                System.err.println("Reads: "+i);
                System.err.println("Data chunks: "+reader.getChunkCount());                
                if (reader.getTextNames().size() > 0) {
                    System.err.println("[Text data]");
                    for (String name: reader.getTextNames()) {
                        System.err.println("["+name+"]");
                        System.err.println(reader.getText(name));
                    }
                }
                System.err.println("File SHA-1: "+StringUtils.byteArrayToString(reader.getDigest()));                
                
            }
            
            // TODO: Actually check for validity... 
            System.err.println((filename.equals("-") ? "stdin": filename) + " is valid");
        } catch (IOException | GeneralSecurityException | RuntimeException e) {
            System.err.println("ERROR: " + e.getMessage());
            if (verbose) {
                e.printStackTrace(System.err);
            }
            System.err.println((filename.equals("-") ? "stdin": filename) + " is not valid!");
            System.exit(1);
        }
    }    
}