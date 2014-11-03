package org.ngsutils.sqz;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import org.ngsutils.fastq.Fastq;
import org.ngsutils.fastq.FastqReader;
import org.ngsutils.fastq.FastqReaderSource;
import org.ngsutils.support.io.PeekableInputStream;

public class SQZFastqReaderSource implements FastqReaderSource {

	static {
		Fastq.registerSource(SQZFastqReaderSource.class);
	}
	
	@Override
	public FastqReader open(InputStream is, String password,
			FileChannel channel, String name) throws IOException {
		try {
			return SQZReader.open(is, false, password, false, channel, name);
		} catch (GeneralSecurityException e) {
			throw new IOException(e);
		}
	}

	@Override
	public boolean autodetect(PeekableInputStream peek) throws IOException {
		byte[] magic = peek.peek(SQZ.MAGIC.length);
		return Arrays.equals(magic, SQZ.MAGIC);
	}

	@Override
	public int getPriority() {
		return 20;
	}

}
