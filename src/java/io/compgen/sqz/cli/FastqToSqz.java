package io.compgen.sqz.cli;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.annotation.Option;
import io.compgen.cmdline.annotation.UnnamedArg;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractCommand;
import io.compgen.common.IterUtils;
import io.compgen.common.StringUtils;
import io.compgen.ngsutils.NGSUtils;
import io.compgen.ngsutils.fastq.Fastq;
import io.compgen.ngsutils.fastq.FastqRead;
import io.compgen.ngsutils.fastq.FastqReader;
import io.compgen.sqz.SQZ;
import io.compgen.sqz.SQZWriter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

@Command(name = "convert", desc = "Converts a FASTQ file (or two paired files) into a SQZ file", category="sqz", experimental=true)
public class FastqToSqz extends AbstractCommand {
    public class AnnotationValue {
        public final String name;
        public final String val;
        public final File file;
        public AnnotationValue(String name, File f) {
            this.name = name;
            this.file = f;
            this.val = null;
        }

        public AnnotationValue(String name, String val) {
            this.name = name;
            this.file = null;
            this.val = val;
        }
    }

    private FastqReader[] readers = null;
	private String outputFilename = null;
    private String password = null;
    private String passwordFile = null;

    private boolean useAES256 = false;
	private boolean force = false;
    private boolean comments = false;
    private boolean colorspace = false;

    private boolean compressDeflate = true;
    private boolean compressBzip2 = false;
	private boolean interleaved = false;
	
	private List<String> inputFilenames = null;
	
	private String curAnnName = null;
    private List<AnnotationValue> annValues = new ArrayList<AnnotationValue>();

	private int chunkSize = 10000;
	
    @UnnamedArg(name="FILE1 {FILE2}")
    public void setFilenames(List<String> files) {
        if (files.size() > 0) {
            try {
                this.readers = new FastqReader[files.size()];
                for (int i=0; i<files.size(); i++) {
                    this.readers[i] = Fastq.open(files.get(i));
                }
            } catch(IOException e) {
                System.err.println(e.getMessage());
                System.exit(1);
            }
        } else {
            System.err.println("You must supply one or two FASTQ files to convert!");
            System.exit(1);
        }
        inputFilenames = files;
    }

    @Option(desc = "Output filename (Default: stdout)", charName = "o", defaultValue="-", name = "output")
    public void setOutputFilename(String outFilename) {
        this.outputFilename = outFilename;
    }

    @Option(desc = "Text annotation name (default: none)", name = "ann-name")
    public void addAnnotationName(String text) {
        curAnnName = text;
    }

    @Option(desc = "Text annotation value (default: none)", name = "ann-val")
    public void addAnnotationValue(String text) {
        if (text != null) {
            if (curAnnName == null) {
                curAnnName = "user";
            }
            this.annValues.add(new AnnotationValue(curAnnName, text));
            curAnnName = null;
        }
    }

    @Option(desc = "Text annotation filename (default:none)", name = "ann-file")
    public void setTextFilename(String textFilename) throws CommandArgumentException {
        if (textFilename != null) {
            if (curAnnName == null) {
                curAnnName = "user";
            }
            File f = new File(textFilename);
            if (!f.exists()) {
                throw new CommandArgumentException("The text annotation file: "+ textFilename+ " does not exist!");
            }
            this.annValues.add(new AnnotationValue(curAnnName, f));
            curAnnName = null;
        }
    }

    @Option(desc = "Encryption password", name = "pass")
    public void setPassword(String password) {
        this.password = password;
    }
    
    @Option(desc = "File containing encryption password", name = "pass-file")
    public void setPasswordFile(String passwordFile) {
        this.passwordFile = passwordFile;
    }
    
    @Option(desc = "Number of reads per compression/encryption block (default: 10000)", name = "block-reads", defaultValue="10000")
    public void setChunkSize(int val) {
        this.chunkSize = val;
    }

    @Option(desc = "Encrypt with AES-256 bit (may require special Java security policy) (default: AES-128 bit)", name = "aes-256")
    public void setAES256(boolean val) {
        this.useAES256 = val;
    }


    
    @Option(desc = "Input file is in colorspace", name = "colorspace")
    public void setColorspace(boolean val) {
        this.colorspace = val;
    }

    @Option(desc = "Force overwriting output file", name = "force")
    public void setForce(boolean val) {
        this.force = val;
    }

    @Option(desc = "Compress file using deflate algorithm (default)", name = "deflate")
    public void setCompressDeflate(boolean val) {
        this.compressDeflate = val;
        this.compressBzip2 = !val;
    }
    
    @Option(desc = "Compress file using bzip2 algorithm (smaller, slower)", name = "bzip2")
    public void setCompressBzip2(boolean val) {
        this.compressBzip2 = val;
        this.compressDeflate = !val;
    }
    
    @Option(desc = "Don't compress the SQZ file", name = "no-compress")
    public void setNoCompress(boolean val) {
        this.compressDeflate = !val;
        this.compressBzip2 = !val;
    }
    
    @Option(desc = "Input FASTQ is interleaved", name = "interleaved")
    public void setInterleaved(boolean val) {
        this.interleaved = val;
    }
    
    @Option(desc = "Include comments field from FASTQ file", name = "comments")
    public void setComments(boolean val) {
        this.comments = val;
    }
    
	@Exec
	public void exec() throws IOException, GeneralSecurityException, CommandArgumentException {
	    if (readers == null) {
            throw new CommandArgumentException("You must supply one or two FASTQ files to convert.");
	    }
        if (interleaved && readers.length > 1) {
            throw new CommandArgumentException("You may not supply more than one FASTQ file in interleaved mode.");
        }

        if (password == null && passwordFile != null) {
            password = StringUtils.strip(new BufferedReader(new FileReader(passwordFile)).readLine());
        }
        
        if (verbose) {
            for (String fname: inputFilenames) {
                System.err.println("Input: "+fname);
            }
            if (comments) {
                System.err.println("Including comments");
            }
            if (colorspace) {
                System.err.println("Input in colorspace");
            }
            if (readers.length > 1) {
                System.err.println("Paired inputs ("+readers.length+")");
            } else if (interleaved) {
                System.err.println("Interleaved input file");
            }
        }

        int flags = 0;
        if (comments) {
            flags |= SQZ.HAS_COMMENTS;
        }
        if (colorspace) {
            flags |= SQZ.COLORSPACE;
        }

        if (interleaved) {
            SQZWriter out = null;
            List<FastqRead> buffer = new ArrayList<FastqRead>();
            for (FastqRead read : readers[0]) {
                if (buffer.size() == 0) {
                    buffer.add(read);
                    continue;
                }

                boolean match = true;
                for (FastqRead test: buffer) {
                    if (!read.getName().equals(test.getName())) {
                        match = false;
                        break;
                    }
                }
                
                if (!match) {
                    if (out == null) {
                        out = buildSQZ(flags, buffer.size());
                    }
                    out.writeReads(buffer, verbose);
                    buffer.clear();
                }
                buffer.add(read);
            }
            if (buffer.size() > 0) {
                if (out == null) {
                    out = buildSQZ(flags, buffer.size());
                }
                out.writeReads(buffer, verbose);
                buffer.clear();
            }
            
            if (annValues.size()>0) {
                for (AnnotationValue ann: annValues) {
                    if (ann.val!=null) {
                        out.writeText(ann.name, ann.val);
                        if (verbose) {
                            System.err.println("Adding text annotation: [" + ann.name+"] " + ann.val);
                        }
                    } else if (ann.file!=null) {
                        out.writeText(ann.name, new FileInputStream(ann.file));
                        if (verbose) {
                            System.err.println("Adding text annotation:  [" + ann.name+"] " + ann.file.getName());
                        }
                    }                        
                }
            }
            
            out.close();
            if (verbose) {
                System.err.println("Data chunks: "+out.getChunkCount());
            }
        } else {
            final SQZWriter out = buildSQZ(flags, readers.length);
            IterUtils.zipArray(readers, new IterUtils.EachList<FastqRead>() {
                long i = 0;
                public void each(List<FastqRead> reads) {
                    if (verbose) {
                        i++;
                        if (i % 100000 == 0) {
                            System.err.println("Read: " + i);
                        }
                    }
                    try {
                        out.writeReads(reads, verbose);
                    } catch (IOException e) {
                        e.printStackTrace();
                        return;
                    }
                }
            });
            if (annValues.size()>0) {
                for (AnnotationValue ann: annValues) {
                    if (ann.val!=null) {
                        out.writeText(ann.name, ann.val);
                        if (verbose) {
                            System.err.println("Adding text annotation: [" + ann.name+"] " + ann.val);
                        }
                    } else if (ann.file!=null) {
                        out.writeText(ann.name, new FileInputStream(ann.file));
                        if (verbose) {
                            System.err.println("Adding text annotation:  [" + ann.name+"] " + ann.file.getName());
                        }
                    }                        
                }
            }
            out.close();
            if (verbose) {
                System.err.println("Data blocks: "+out.getChunkCount());
            }
        }
        for (FastqReader reader: readers) {
            reader.close();
        }
	}

	private SQZWriter buildSQZ(int flags, int readCount) throws IOException, GeneralSecurityException, CommandArgumentException {
	    SQZWriter out=null;
	    
        if (outputFilename.equals("-")) {
            out = new SQZWriter(System.out, flags, readCount, SQZ.COMPRESS_NONE, password == null ? null: (useAES256 ? "AES-256": "AES-128"), password);
            if (verbose) {
                System.err.println("Output: stdout (uncompressed)");
                System.err.println("Encryption: " + (password == null ? "no": (useAES256 ? "AES-256": "AES-128")));
            }
        } else {
            if (new File(outputFilename).exists() && !force) {
                throw new CommandArgumentException("The output file: "+outputFilename+" exists! Use --force to overwrite.");
            }
            int compressionType;
            
            if (compressDeflate) {
                compressionType = SQZ.COMPRESS_DEFLATE;
            } else if (compressBzip2) {
                compressionType = SQZ.COMPRESS_BZIP2;
            } else {
                compressionType = SQZ.COMPRESS_NONE;
            }
            out = new SQZWriter(outputFilename, flags, readCount, compressionType, password == null ? null: (useAES256 ? "AES-256": "AES-128"), password);

            if (verbose) {
                System.err.println("Output: "+outputFilename);
                System.err.println("Encryption: " + (password == null ? "no": (useAES256 ? "AES-256": "AES-128")));
                System.err.println("Compression: " +compressionType);
            }
        }
        
        out.setChunkSize(chunkSize);
        
        if (verbose) {
            System.err.println("Reads per block: "+chunkSize);
        }

        out.writeText("SQZ", "{ \"version\": \"" + NGSUtils.getVersion()+ "\", \"cmdline\":\"" + NGSUtils.getArgs()+"\"}");
        
        return out;

	}
}

