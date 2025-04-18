/*
 *                    BioJava development code
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  If you do not have a copy,
 * see:
 *
 *      http://www.gnu.org/copyleft/lesser.html
 *
 * Copyright for this code is held jointly by the individual
 * authors.  These should be listed in @author doc comments.
 *
 * For more information on the BioJava project and its aims,
 * or to join the biojava-l mailing list, visit the home page
 * at:
 *
 *      http://www.biojava.org/
 *
 */
package org.biojava.nbio.core.sequence.io;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

/**
 * Need to keep track of actual bytes read and take advantage of buffered reader
 * performance. Took java source for BufferedReader and added BytesRead functionality<br>
 * ---------- original buffered reader ----------------------<BR>
 * Reads text from a character-input stream, buffering characters so as to
 * provide for the efficient reading of characters, arrays, and lines.
 *
 * <p> The buffer size may be specified, or the default size may be used.  The
 * default is large enough for most purposes.
 *
 * <p> In general, each read request made of a Reader causes a corresponding
 * read request to be made of the underlying character or byte stream.  It is
 * therefore advisable to wrap a BufferedReaderBytesRead around any Reader whose read()
 * operations may be costly, such as FileReaders and InputStreamReaders.  For
 * example,
 *
 * <pre>
 * BufferedReaderBytesRead in
 *   = new BufferedReaderBytesRead(new FileReader("foo.in"));
 * </pre>
 *
 * will buffer the input from the specified file.  Without buffering, each
 * invocation of read() or readLine() could cause bytes to be read from the
 * file, converted into characters, and then returned, which can be very
 * inefficient.
 *
 * <p> Programs that use DataInputStreams for textual input can be localized by
 * replacing each DataInputStream with an appropriate BufferedReaderBytesRead.
 *
 * @see FileReader
 * @see InputStreamReader
 *
 * @version 	1.37, 06/03/15
 * @author	Mark Reinhold
 * @author Scooter Willis &lt;willishf at gmail dot com&gt;
 * @since	JDK1.1
 */
public class BufferedReaderBytesRead extends Reader {

	private Reader in;
	private char[] cb;
	private int nChars, nextChar;
	private static final int INVALIDATED = -2;
	private static final int UNMARKED = -1;
	private int markedChar = UNMARKED;
	private int readAheadLimit = 0; /* Valid only when markedChar > 0 */

	/** If the next character is a line feed, skip it */
	private boolean skipLF = false;
	/** The skipLF flag when the mark was set */
	private boolean markedSkipLF = false;
	private static final int defaultCharBufferSize = 8192;
	private static final int defaultExpectedLineLength = 80;
	long bytesRead = 0;

	/**
	 * Creates a buffering character-input stream that uses an input buffer of
	 * the specified size.
	 *
	 * @param  in   A Reader
	 * @param  sz   Input-buffer size
	 *
	 * @exception  IllegalArgumentException  If sz is &lt;= 0
	 */
	public BufferedReaderBytesRead(Reader in, int sz) {
		super(in);
		if (sz <= 0) {
			throw new IllegalArgumentException("Buffer size <= 0");
		}
		this.in = in;
		cb = new char[sz];
		nextChar = nChars = 0;
	}

	/**
	 * Creates a buffering character-input stream that uses a default-sized
	 * input buffer.
	 *
	 * @param  in   A Reader
	 */
	public BufferedReaderBytesRead(Reader in) {
		this(in, defaultCharBufferSize);
	}

	/**
	 * Keep track of bytesread via ReadLine to account for CR-LF in the stream. Does not keep track of position if
	 * use methods other than ReadLine.
	 * //TODO should override other methods and throw exception or keep track of bytes read
	 * @return
	 */
	public long getBytesRead() {
		return bytesRead;
	}

	/** Checks to make sure that the stream has not been closed */
	private void ensureOpen() throws IOException {
		if (in == null) {
			throw new IOException("Stream closed");
		}
	}

	/**
	 * Fills the input buffer, taking the mark into account if it is valid.
	 */
	private void fill() throws IOException {
		int dst;
		if (markedChar <= UNMARKED) {
			/* No mark */
			dst = 0;
		} else {
			/* Marked */
			int delta = nextChar - markedChar;
			if (delta >= readAheadLimit) {
				/* Gone past read-ahead limit: Invalidate mark */
				markedChar = INVALIDATED;
				readAheadLimit = 0;
				dst = 0;
			} else {
				if (readAheadLimit <= cb.length) {
					/* Shuffle in the current buffer */
					System.arraycopy(cb, markedChar, cb, 0, delta);
					markedChar = 0;
					dst = delta;
				} else {
					/* Reallocate buffer to accommodate read-ahead limit */
					char[] ncb = new char[readAheadLimit];
					System.arraycopy(cb, markedChar, ncb, 0, delta);
					cb = ncb;
					markedChar = 0;
					dst = delta;
				}
				nextChar = nChars = delta;
			}
		}

		int n;
		do {
			n = in.read(cb, dst, cb.length - dst);
		} while (n == 0);
		if (n > 0) {
			nChars = dst + n;
			nextChar = dst;
		}
	}

	/**
	 * Reads a single character.
	 *
	 * @return The character read, as an integer in the range
	 *         0 to 65535 (<code>0x00-0xffff</code>), or -1 if the
	 *         end of the stream has been reached
	 * @exception  IOException  If an I/O error occurs
	 */
	@Override
	public int read() throws IOException {
		synchronized (lock) {
			ensureOpen();
			for (;;) {
				if (nextChar >= nChars) {
					fill();
					if (nextChar >= nChars) {
						return -1;
					}
				}
				if (skipLF) {
					skipLF = false;
					if (cb[nextChar] == '\n') {
						bytesRead++;
						nextChar++;
						continue;
					}
				}
				bytesRead++;
				return cb[nextChar++];
			}
		}
	}

	/**
	 * Reads characters into a portion of an array, reading from the underlying
	 * stream if necessary.
	 */
	private int read1(char[] cbuf, int off, int len) throws IOException {
		if (nextChar >= nChars) {
			/* If the requested length is at least as large as the buffer, and
			if there is no mark/reset activity, and if line feeds are not
			being skipped, do not bother to copy the characters into the
			local buffer.  In this way buffered streams will cascade
			harmlessly. */
			if (len >= cb.length && markedChar <= UNMARKED && !skipLF) {
				return in.read(cbuf, off, len);
			}
			fill();
		}
		if (nextChar >= nChars) {
			return -1;
		}
		if (skipLF) {
			skipLF = false;
			if (cb[nextChar] == '\n') {
				nextChar++;
				if (nextChar >= nChars) {
					fill();
				}
				if (nextChar >= nChars) {
					return -1;
				}
			}
		}
		int n = Math.min(len, nChars - nextChar);
		System.arraycopy(cb, nextChar, cbuf, off, n);
		nextChar += n;
		return n;
	}

	/**
	 * Reads characters into a portion of an array.
	 *
	 * <p> This method implements the general contract of the corresponding
	 * <code>{@link Reader#read(char[], int, int) read}</code> method of the
	 * <code>{@link Reader}</code> class.  As an additional convenience, it
	 * attempts to read as many characters as possible by repeatedly invoking
	 * the <code>read</code> method of the underlying stream.  This iterated
	 * <code>read</code> continues until one of the following conditions becomes
	 * true: <ul>
	 *
	 *   <li> The specified number of characters have been read,
	 *
	 *   <li> The <code>read</code> method of the underlying stream returns
	 *   <code>-1</code>, indicating end-of-file, or
	 *
	 *   <li> The <code>ready</code> method of the underlying stream
	 *   returns <code>false</code>, indicating that further input requests
	 *   would block.
	 *
	 * </ul> If the first <code>read</code> on the underlying stream returns
	 * <code>-1</code> to indicate end-of-file then this method returns
	 * <code>-1</code>.  Otherwise this method returns the number of characters
	 * actually read.
	 *
	 * <p> Subclasses of this class are encouraged, but not required, to
	 * attempt to read as many characters as possible in the same fashion.
	 *
	 * <p> Ordinarily this method takes characters from this stream's character
	 * buffer, filling it from the underlying stream as necessary.  If,
	 * however, the buffer is empty, the mark is not valid, and the requested
	 * length is at least as large as the buffer, then this method will read
	 * characters directly from the underlying stream into the given array.
	 * Thus redundant <code>BufferedReaderBytesRead</code>s will not copy data
	 * unnecessarily.
	 *
	 * @param      cbuf  Destination buffer
	 * @param      off   Offset at which to start storing characters
	 * @param      len   Maximum number of characters to read
	 *
	 * @return     The number of characters read, or -1 if the end of the
	 *             stream has been reached
	 *
	 * @exception  IOException  If an I/O error occurs
	 */
	@Override
	public int read(char[] cbuf, int off, int len) throws IOException {
		synchronized (lock) {
			ensureOpen();
			if ((off < 0) || (off > cbuf.length) || (len < 0)
					|| ((off + len) > cbuf.length) || ((off + len) < 0)) {
				throw new IndexOutOfBoundsException();
			} else if (len == 0) {
				return 0;
			}

			int n = read1(cbuf, off, len);
			if (n <= 0) {
				return n;
			}
			while ((n < len) && in.ready()) {
				int n1 = read1(cbuf, off + n, len - n);
				if (n1 <= 0) {
					break;
				}
				n += n1;
			}
			bytesRead = bytesRead + n;
			return n;
		}
	}

	/**
	 * Reads a line of text.  A line is considered to be terminated by any one
	 * of a line feed ('\n'), a carriage return ('\r'), or a carriage return
	 * followed immediately by a linefeed.
	 *
	 * @param      ignoreLF  If true, the next '\n' will be skipped
	 *
	 * @return     A String containing the contents of the line, not including
	 *             any line-termination characters, or null if the end of the
	 *             stream has been reached
	 *
	 * @see        java.io.LineNumberReader#readLine()
	 *
	 * @exception  IOException  If an I/O error occurs
	 */
	@SuppressWarnings("unused")
	private String readLine(boolean ignoreLF) throws IOException {
		StringBuffer s = null;
		int startChar;

		synchronized (lock) {
			ensureOpen();
			boolean omitLF = ignoreLF || skipLF;

			bufferLoop:
			for (;;) {

				if (nextChar >= nChars) {
					fill();
				}
				if (nextChar >= nChars) { /* EOF */
					if (s != null && s.length() > 0) {

						return s.toString();
					} else {
						return null;
					}
				}
				boolean eol = false;
				char c = 0;
				int i;

				/* Skip a leftover '\n', if necessary */
				if (omitLF && (cb[nextChar] == '\n')) {
					nextChar++;
					bytesRead++;
				}
				skipLF = false;
				omitLF = false;

				charLoop:
				for (i = nextChar; i < nChars; i++) {
					c = cb[i];
					if ((c == '\n') || (c == '\r')) {
						bytesRead++;
						eol = true;
						break charLoop;
					}
				}

				startChar = nextChar;
				nextChar = i;

				if (eol) {
					String str;
					if (s == null) {
						str = new String(cb, startChar, i - startChar);
					} else {
						s.append(cb, startChar, i - startChar);
						str = s.toString();
					}
					nextChar++;
					if (c == '\r') {
						bytesRead++;
						skipLF = true;
					}

					return str;
				}

				if (s == null) {
					s = new StringBuffer(defaultExpectedLineLength);
				}
				s.append(cb, startChar, i - startChar);

			}
		}
	}

	/**
	 * Reads a line of text.  A line is considered to be terminated by any one
	 * of a line feed ('\n'), a carriage return ('\r'), or a carriage return
	 * followed immediately by a linefeed.
	 *
	 * @return     A String containing the contents of the line, not including
	 *             any line-termination characters, or null if the end of the
	 *             stream has been reached
	 *
	 * @exception  IOException  If an I/O error occurs
	 */
	public String readLine() throws IOException {
		String line = readLine(false);
		if (line != null) {
			bytesRead = bytesRead + line.length();
		}
		return line;
	}

	/**
	 * Skips characters.
	 *
	 * @param  n  The number of characters to skip
	 *
	 * @return    The number of characters actually skipped
	 *
	 * @exception  IllegalArgumentException  If <code>n</code> is negative.
	 * @exception  IOException  If an I/O error occurs
	 */
	@Override
	public long skip(long n) throws IOException {
		if (n < 0L) {
			throw new IllegalArgumentException("skip value is negative");
		}
		synchronized (lock) {
			ensureOpen();
			long r = n;
			while (r > 0) {
				if (nextChar >= nChars) {
					fill();
				}
				if (nextChar >= nChars) /* EOF */ {
					break;
				}
				if (skipLF) {
					skipLF = false;
					if (cb[nextChar] == '\n') {
						nextChar++;
					}
				}
				long d = (long)nChars - nextChar;
				if (r <= d) {
					nextChar += r;
					r = 0;
					break;
				} else {
					r -= d;
					nextChar = nChars;
				}
			}
			bytesRead = bytesRead + (n - r);
			return n - r;
		}
	}

	/**
	 * Tells whether this stream is ready to be read.  A buffered character
	 * stream is ready if the buffer is not empty, or if the underlying
	 * character stream is ready.
	 *
	 * @exception  IOException  If an I/O error occurs
	 */
	@Override
	public boolean ready() throws IOException {
		synchronized (lock) {
			ensureOpen();

			/*
			 * If newline needs to be skipped and the next char to be read
			 * is a newline character, then just skip it right away.
			 */
			if (skipLF) {
				/* Note that in.ready() will return true if and only if the next
				 * read on the stream will not block.
				 */
				if (nextChar >= nChars && in.ready()) {
					fill();
				}
				if (nextChar < nChars) {
					if (cb[nextChar] == '\n') {
						nextChar++;
					}
					skipLF = false;
				}
			}
			return (nextChar < nChars) || in.ready();
		}
	}

	/**
	 * Tells whether this stream supports the mark() operation, which it does.
	 */
	@Override
	public boolean markSupported() {
		return true;
	}

	/**
	 * Marks the present position in the stream.  Subsequent calls to reset()
	 * will attempt to reposition the stream to this point.
	 *
	 * @param readAheadLimit   Limit on the number of characters that may be
	 *                         read while still preserving the mark. An attempt
	 *                         to reset the stream after reading characters
	 *                         up to this limit or beyond may fail.
	 *                         A limit value larger than the size of the input
	 *                         buffer will cause a new buffer to be allocated
	 *                         whose size is no smaller than limit.
	 *                         Therefore large values should be used with care.
	 *
	 * @exception  IllegalArgumentException  If readAheadLimit is &lt; 0
	 * @exception  IOException  If an I/O error occurs
	 */
	@Override
	public void mark(int readAheadLimit) throws IOException {
		if (readAheadLimit < 0) {
			throw new IllegalArgumentException("Read-ahead limit < 0");
		}
		synchronized (lock) {
			ensureOpen();
			this.readAheadLimit = readAheadLimit;
			markedChar = nextChar;
			markedSkipLF = skipLF;
		}
	}

	/**
	 * Resets the stream to the most recent mark.
	 *
	 * @exception  IOException  If the stream has never been marked,
	 *                          or if the mark has been invalidated
	 */
	@Override
	public void reset() throws IOException {
		synchronized (lock) {
			ensureOpen();
			if (markedChar < 0) {
				throw new IOException((markedChar == INVALIDATED)
						? "Mark invalid"
						: "Stream not marked");
			}
			nextChar = markedChar;
			skipLF = markedSkipLF;
		}
	}

	@Override
	public void close() throws IOException {
		synchronized (lock) {
			if (in == null) {
				return;
			}
			in.close();
			in = null;
			cb = null;
		}
	}
}

