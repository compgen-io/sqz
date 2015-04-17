package io.compgen.sqz.cli;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractCommand;
import io.compgen.common.StringUtils;
import io.compgen.ngsutils.fastq.FastqRead;
import io.compgen.sqz.SQZReader;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.zip.GZIPOutputStream;

@Command(name="export", desc="Export the read sequences from an SQZ file to FASTQ format", category="sqz", experimental=true)
public class SqzToFastq extends AbstractCommand {
    
    private String filename=null;
    private String outTemplate=null;
    private String password = null;
    private String passwordFile = null;

    private boolean split = false;
    private boolean compress = false;
    private boolean force = false;
    private boolean ignoreComments = false;
    
    private boolean first = false;
    private boolean second = false;
    
    @UnnamedArg(name = "INFILE")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Option(desc = "Output filename template (default: stdout)", name="out")
    public void setOutTemplate(String outTemplate) {
        this.outTemplate = outTemplate;
    }

    @Option(desc = "Decryption password", name = "pass")
    public void setPassword(String password) {
        this.password = password;
    }
    
    @Option(desc = "File containing decryption password", name = "pass-file")
    public void setPasswordFile(String passwordFile) {
        this.passwordFile = passwordFile;
    }
    
    @Option(desc = "Force overwriting output", name="force")
    public void setForce(boolean val) {
        this.force = val;
    }

    @Option(desc = "Compress output", name="gz")
    public void setCompress(boolean val) {
        this.compress = val;
    }

    @Option(desc = "Split output into paired files (default: interleaved)", name="split")
    public void setSplit(boolean val) {
        this.split = val;
    }

    @Option(desc = "Ouput only first reads (default: output both)", name="first")
    public void setFirst(boolean val) {
        this.first = val;
    }

    @Option(desc = "Ouput only second reads (default: output both)", name="second")
    public void setSecond(boolean val) {
        this.second = val;
    }

    @Option(desc = "Don't write comments (if present)", name = "ignore-comments")
    public void setNoComments(boolean val) {
        this.ignoreComments = val;
    }

    @Exec
    public void exec() throws CommandArgumentException {        
        if (filename == null) {
            throw new CommandArgumentException("You must specify an input SQZ file!");
        }
        if (split && (outTemplate == null || outTemplate.equals("-"))) {
            throw new CommandArgumentException("You cannot have split output to stdout!");
        }

        if (first && second) {
            throw new CommandArgumentException("You can not use --first and --second at the same time!");
        }

        if (split && (first || second)) {
            throw new CommandArgumentException("You can not use --split and --first or --second at the same time!");
        }
        try{
            if (password == null && passwordFile != null) {
                    password = StringUtils.strip(new BufferedReader(new FileReader(passwordFile)).readLine());
            }
            
            SQZReader reader;
            if (filename.equals("-")) {
                reader = SQZReader.open(System.in, ignoreComments, password, verbose);
                if (verbose) {
                    System.err.println("Input: stdin");
                }
            } else {
                reader = SQZReader.open(filename, ignoreComments, password, verbose);
                if (verbose) {
                    System.err.println("Input: " + filename);
                }
            }
    
            OutputStream[] outs;
            if (outTemplate==null || outTemplate.equals("-")) {
                outs = new OutputStream[] { new BufferedOutputStream(System.out) };
                if (verbose) {
                    System.err.println("Output: stdout");
                }
            } else if (outTemplate != null && (first || second)) {
                String outFilename;
                if (compress) {
                    if (first) { 
                        outFilename = outTemplate+"_R1.fastq.gz";
                    } else {
                        outFilename = outTemplate+"_R2.fastq.gz";
                    }
                } else {
                    if (first) { 
                        outFilename = outTemplate+"_R1.fastq";
                    } else {
                        outFilename = outTemplate+"_R2.fastq";
                    }
                }
                if (new File(outFilename).exists() && !force) {
                    reader.close();
                    throw new CommandArgumentException("Output file: "+ outFilename+" exists! Use --force to overwrite!");
                }
                if (verbose) {
                    System.err.println("Output: " + outFilename);
                }
                if (compress) {
                    outs = new OutputStream[] { new GZIPOutputStream(new FileOutputStream(outFilename)) };
                } else {
                    outs = new OutputStream[] { new BufferedOutputStream(new FileOutputStream(outFilename)) };
                }
            } else if (outTemplate != null && split) {
                String[] outFilenames;
                if (compress) {
                    outFilenames = new String[] { outTemplate+"_R1.fastq.gz", outTemplate+"_R2.fastq.gz" };
                } else {
                    outFilenames = new String[] {outTemplate+"_R1.fastq", outTemplate+"_R2.fastq"};
                }
                for (String outFilename: outFilenames) {
                    if (new File(outFilename).exists() && !force) {
                        reader.close();
                        throw new CommandArgumentException("Output file: "+ outFilename+" exists! Use --force to overwrite!");
                    }
                    if (verbose) {
                        System.err.println("Output: " + outFilename);
                    }
                }
    
                outs = new OutputStream[2];
                if (compress) {
                    outs[0] = new GZIPOutputStream(new FileOutputStream(outFilenames[0]));
                    outs[1] = new GZIPOutputStream(new FileOutputStream(outFilenames[1]));                
                } else {
                    outs[0] = new BufferedOutputStream(new FileOutputStream(outFilenames[0]));
                    outs[1] = new BufferedOutputStream(new FileOutputStream(outFilenames[1]));                
                }
            } else {
                String outFilename;
                if (compress) {
                    outFilename = outTemplate+".fastq.gz";
                } else {
                    outFilename = outTemplate+".fastq";
                }
                if (new File(outFilename).exists() && !force) {
                    reader.close();
                    throw new CommandArgumentException("Output file: "+ outFilename+" exists! Use --force to overwrite!");
                }
                if (verbose) {
                    System.err.println("Output: " + outFilename);
                }
                if (compress) {
                    outs = new OutputStream[] { new GZIPOutputStream(new FileOutputStream(outFilename)) };
                } else {
                    outs = new OutputStream[] { new BufferedOutputStream(new FileOutputStream(outFilename)) };
                }
            }
    
            String lastName = null;
    
            for (FastqRead read: reader) {
                if (split && read.getName().equals(lastName)) {
                    read.write(outs[1]);
                } else {
                    if (
                            (first && !read.getName().equals(lastName)) || 
                            (second && read.getName().equals(lastName)) || 
                            (!first && !second)
                        ) {
                        read.write(outs[0]);
                    }
                }
                lastName = read.getName();
            }
            for (OutputStream out: outs) {
                out.close();
            }
            reader.close();
        } catch (IOException | GeneralSecurityException | RuntimeException e) {
            System.err.println("ERROR: " + e.getMessage());
            if (verbose) {
                e.printStackTrace(System.err);
            }
            System.exit(1);
        }
    }    
}
