/*
 * The MIT License (MIT)
 * 
 * Copyright (c) 2015 Tinotenda Chemvura
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.openstatic.sound.dtmf;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import javax.sound.sampled.UnsupportedAudioFileException;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;
import org.openstatic.sound.MixerStream;

/**
 * Class to decode DTMF signals in a supported audio file.
 * 
 * @author Tinotenda Chemvura
 *
 */
public class DTMFUtil {

	/**
	 * True if the decoder is to be used in debug mode. False by default
	 */

	public static boolean debug = false;
	/**
	 * True if decoder is to use the goertzel algorithm instead of the FFT False
	 * by default
	 */
	public static boolean goertzel = true;

	private static final double CUT_OFF_POWER = 0.004;
	private static final double FFT_CUT_OFF_POWER_NOISE_RATIO = 0.46;
	private static final double FFT_FRAME_DURATION = 0.030;
	private static final double GOERTZEL_CUT_OFF_POWER_NOISE_RATIO = 0.87;
	private static final double GOERTZEL_FRAME_DURATION = 0.045;

	private boolean decoded;

	private boolean decoder = false;
	private boolean generate = false;

	private String seq[];
	private MixerStream audio;
	private int frameSize;

	private static int[] freqIndicies;

	/**
	 * The list of valid DTMF frequencies that are going to be processed and
	 * searched for within the ITU-T recommendations . See the <a href=
	 * "http://en.wikipedia.org/wiki/Dual-tone_multi-frequency_signaling" >
	 * WikiPedia article on DTMF</a>.
	 */
	public static final int[] DTMF_FREQUENCIES_BIN = { 
			687, 697, 707, // 697
			758, 770, 782, // 770
			839, 852, 865, // 852
			927, 941, 955, // 941
			1191, 1209, 1227, // 1209
			1316, 1336, 1356, // 1336
			1455, 1477, 1499, // 1477
			1609, 1633, 1647, 1657 // 1633
	};

	/**
	 * The list of valid DTMF frequencies. See the <a href=
	 * "http://en.wikipedia.org/wiki/Dual-tone_multi-frequency_signaling" >
	 * WikiPedia article on DTMF</a>.
	 */
	public static final int[] DTMF_FREQUENCIES = { 697, 770, 852, 941, 1209, 1336, 1477, 1633 };

	/**
	 * The list of valid DTMF characters. See the <a href=
	 * "http://en.wikipedia.org/wiki/Dual-tone_multi-frequency_signaling" >
	 * WikiPedia article on DTMF</a>.
	 */
	public static final char[] DTMF_CHARACTERS = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd',
			'A', 'B', 'C', 'D' };

	// generation variables
	private double[] generatedSeq;
	private int outPauseDurr;
	private int outToneDurr;
	private double outFs;
	private char[] outChars;
	private boolean generated;

	/**
	 * Constructor used to Decode an audio file
	 * 
	 * @param data
	 *            AudioFile object to be processed.
	 * @throws DTMFDecoderException
	 */
	public DTMFUtil(MixerStream data) {
		this.decoder = true;
		this.audio = data;
		setFrameSize();
		if (!goertzel)
			setCentreIndicies();
		this.decoded = false;
		seq = new String[2];
		this.seq[0] = "";
		this.seq[1] = "";
	}

	/**
	 * Check if the characters are valid and set the characters
	 * 
	 * @param chars
	 *            Characters to be generated
	 * @throws DTMFDecoderException
	 */
	private void setChars(char[] chars) throws DTMFDecoderException {
		outChars = new char[chars.length];
		char[] cc = Arrays.copyOf(DTMF_CHARACTERS, DTMF_CHARACTERS.length);
		Arrays.sort(cc);
		for (int c = 0; c < chars.length; c++) {
			if (Arrays.binarySearch(cc, chars[c]) < 0)
				throw new DTMFDecoderException("The character \"" + chars[c] + "\" is not a DTMF character.");
			else
				outChars[c] = chars[c];
		}
	}

	/**
	 * Method to precalculate the indices to be used to locate the DTMF
	 * frequencies in the power spectrum
	 */
	private void setCentreIndicies() {
		freqIndicies = new int[DTMF_FREQUENCIES_BIN.length];
		for (int i = 0; i < freqIndicies.length; i++) {
			int ind = (int) Math.round(((DTMF_FREQUENCIES_BIN[i] * 1.0) / (audio.getSampleRate()) * 1.0) * frameSize);
			freqIndicies[i] = ind;
		}
	}

	/**
	 * Method to set the frame size for the decoding process. Framesize must be
	 * a power of 2
	 * 
	 * @throws DTMFDecoderException
	 *             If Fs if less than 8kHz or loo large.
	 */
	private void setFrameSize() {
		if (goertzel) {
			this.frameSize = (int) Math.floor(GOERTZEL_FRAME_DURATION * audio.getSampleRate());
		} else {
			int size = 0;
			for (int i = 8; i <= 15; i++) {
				size = (int) Math.pow(2, i);
				if (size / (audio.getSampleRate() * 1.0) < FFT_FRAME_DURATION)
					continue;
				else {
					frameSize = size;
					return;
				}
			}
		}
	}

	public int getFrameSize()
	{
		return this.frameSize;
	}

	/**
	 * Method to filter out the power spectrum information for the DTMF
	 * frequencies given an array of power spectrum information from an FFT.
	 * 
	 * @param frame
	 *            Frame with power spectrum information to be processed
	 * @return an array with 8 doubles. Each representing the magnitude of the
	 *         corresponding dtmf frequency
	 */
	private static double[] filterFrame(double[] frame) {
		double[] out = new double[8];

		// 687, 697, 707, // 697 0,1,2
		// 758, 770, 782, // 770 3,4,5
		// 839, 852, 865, // 852 6,7,8
		// 927, 941, 955, // 941 9,10,11
		// 1191, 1209, 1227, // 1209 12,13,14
		// 1316, 1336, 1356, // 1336 15,16,17
		// 1455, 1477, 1499, // 1477 18,19,20
		// 1609, 1633, 1647, 1657 // 1633 21,22,23,24

		// 687, 697, 707, // 697 0,1,2
		out[0] = frame[freqIndicies[0]];
		if (freqIndicies[0] != freqIndicies[1])
			out[0] += frame[freqIndicies[1]];
		if (freqIndicies[0] != freqIndicies[2] && freqIndicies[1] != freqIndicies[2])
			out[0] += frame[freqIndicies[2]];

		// 758, 770, 782, // 770 3,4,5
		out[1] = frame[freqIndicies[3]];
		if (freqIndicies[3] != freqIndicies[4])
			out[1] += frame[freqIndicies[4]];
		if (freqIndicies[3] != freqIndicies[5] && freqIndicies[4] != freqIndicies[5])
			out[1] += frame[freqIndicies[5]];

		// 839, 852, 865, // 852 6,7,8
		out[2] = frame[freqIndicies[6]];
		if (freqIndicies[6] != freqIndicies[7])
			out[2] += frame[freqIndicies[7]];
		if (freqIndicies[6] != freqIndicies[8] && freqIndicies[7] != freqIndicies[8])
			out[2] += frame[freqIndicies[8]];

		// 927, 941, 955, // 941 9,10,11
		out[3] = frame[freqIndicies[9]];
		if (freqIndicies[9] != freqIndicies[10])
			out[3] += frame[freqIndicies[10]];
		if (freqIndicies[9] != freqIndicies[11] && freqIndicies[10] != freqIndicies[11])
			out[3] += frame[freqIndicies[11]];

		// 1191, 1209, 1227, // 1209 12,13,14
		out[4] = frame[freqIndicies[12]];
		if (freqIndicies[12] != freqIndicies[13])
			out[4] += frame[freqIndicies[13]];
		if (freqIndicies[12] != freqIndicies[14] && freqIndicies[13] != freqIndicies[14])
			out[5] += frame[freqIndicies[14]];

		// 1316, 1336, 1356, // 1336 15,16,17
		out[5] = frame[freqIndicies[15]];
		if (freqIndicies[15] != freqIndicies[16])
			out[5] += frame[freqIndicies[16]];
		if (freqIndicies[15] != freqIndicies[17] && freqIndicies[16] != freqIndicies[17])
			out[5] += frame[freqIndicies[17]];

		// 1455, 1477, 1499, // 1477 18,19,20
		out[6] = frame[freqIndicies[18]];
		if (freqIndicies[18] != freqIndicies[19])
			out[6] += frame[freqIndicies[19]];
		if (freqIndicies[18] != freqIndicies[20] && freqIndicies[19] != freqIndicies[20])
			out[6] += frame[freqIndicies[20]];

		out[7] = frame[freqIndicies[21]];
		if (frame[freqIndicies[22]] != frame[freqIndicies[21]])
			out[7] += frame[freqIndicies[22]];
		else
			out[7] += frame[freqIndicies[23]];
		out[7] += frame[freqIndicies[24]];

		return out;
	}

	/**
	 * Method returns the DTMF sequence
	 * 
	 * @return char array with the keys represented in the file
	 * @throws DTMFDecoderException
	 *             Throws excepion when the file has not been decoded yet.
	 */
	public String[] getDecoded() throws DTMFDecoderException {
		if (!decoded)
			throw new DTMFDecoderException("File has not been decoded yet. Please run the method decode() first!");
		return seq;
	}

	/**
	 * Method to generate a frequency spectrum of the frame using FFT
	 * 
	 * @param frame
	 *            Frame to be transformed
	 * @param Fs
	 *            Sampling Frequency
	 * @return an Array showing the realtive powers of all frequencies
	 */
	private static double[] transformFrameFFT(double[] frame, int Fs) {
		final FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);
		final Complex[] spectrum = fft.transform(frame, TransformType.FORWARD);
		final double[] powerSpectrum = new double[frame.length / 2 + 1];
		for (int ii = 0; ii < powerSpectrum.length; ii++) {
			final double abs = spectrum[ii].abs();
			powerSpectrum[ii] = abs * abs;
		}
		return powerSpectrum;
	}

	/**
	 * Method to generate a frequency spectrum of the frame using Goertzel
	 * Algorithm
	 * 
	 * @param frame
	 *            Frame to be transformed
	 * @param Fs
	 *            Sampling Frequency
	 * @return an Array showing the realtive powers of the DTMF frequencies
	 * @throws DTMFDecoderException
	 *             If no samples have been provided for the goertze class to
	 *             transform
	 */
	private static double[] transformFrameG(double[] frame, int Fs) throws DTMFDecoderException {
		double[] out;
		GoertzelOptimised g = new GoertzelOptimised(Fs, frame, DTMF_FREQUENCIES_BIN);
		// 1. transform the frames using goertzel algorithm
		// 2. get the highest DTMF freq within the tolerance range and use that
		// magnitude to represet the corresponsing DTMF free
		if (g.compute()) {
			out = filterFrameG(g.getMagnitudeSquared());
			return out;
		} else {
			throw new DTMFDecoderException("Decoding failed.");
		}
	}

	/**
	 * Method to get the highest DTMF freq within the tolerance range and use
	 * that magnitude to represet the corresponsing DTMF freq
	 * 
	 * @param frame
	 *            Frame with 274 magnitudes to be processed
	 * @return an array with 8 magnitudes. Each representing the magnitude of
	 *         each frequency
	 */
	private static double[] filterFrameG(double[] frame) {
		double[] out = new double[8];
		out[0] = DecoderUtil.max(Arrays.copyOfRange(frame, 0, 3));
		out[1] = DecoderUtil.max(Arrays.copyOfRange(frame, 3, 6));
		out[2] = DecoderUtil.max(Arrays.copyOfRange(frame, 6, 9));
		out[3] = DecoderUtil.max(Arrays.copyOfRange(frame, 9, 12));
		out[4] = DecoderUtil.max(Arrays.copyOfRange(frame, 12, 15));
		out[5] = DecoderUtil.max(Arrays.copyOfRange(frame, 15, 18));
		out[6] = DecoderUtil.max(Arrays.copyOfRange(frame, 18, 21));
		out[7] = DecoderUtil.max(Arrays.copyOfRange(frame, 21, 25));
		return out;
	}

	/**
	 * Method to detect whether a frame is too noisy for detection
	 * 
	 * @param dft_data
	 *            Frequency spectrum magnitudes for the DTMF frequencies
	 * @param power_spectrum
	 * @return true is noisy or false if it is acceptable
	 */
	private boolean isNoisy(double[] dft_data, double[] power_spectrum) {
		if (power_spectrum == null)
			return true;
		// sum the powers of all frequencies = sum
		// find ratio of the (sum of two highest peaks) : sum
		double[] temp1 = Arrays.copyOfRange(dft_data, 0, 4);
		double[] temp2 = Arrays.copyOfRange(dft_data, 4, 8);
		Arrays.sort(temp1);
		Arrays.sort(temp2);
		// ratio = (max(lower freqs) + max(higher freqs))/sum(all freqs in
		// spectrum)
		return ((temp1[temp1.length - 1] + temp2[temp2.length - 1])
				/ DecoderUtil.sumArray(power_spectrum)) < FFT_CUT_OFF_POWER_NOISE_RATIO;
	}

	private boolean isNoisyG(double[] dft_data) {
		// sum the powers of all frequencies = sum
		// find ratio of the (sum of two highest peaks) : sum
		double[] temp1 = Arrays.copyOfRange(dft_data, 0, 4);
		double[] temp2 = Arrays.copyOfRange(dft_data, 4, 8);
		Arrays.sort(temp1);
		Arrays.sort(temp2);
		double one = temp1[temp1.length - 1];
		double two = temp2[temp2.length - 1];
		double sum = DecoderUtil.sumArray(dft_data);
		return ((one + two) / sum) < GOERTZEL_CUT_OFF_POWER_NOISE_RATIO;
	}

	/**
	 * Method to decode a frame given the frequency spectrum information of the
	 * frame
	 * 
	 * @param dft_data
	 *            Frequency spectrum information showing the relative magnitudes
	 *            of the power of each DTMF frequency
	 * @return DTMF charatcter represented by the frame
	 * @throws DTMFDecoderException
	 */
	private static char getRawChar(double[] dft_data) throws DTMFDecoderException {
		char out = 0;
		int low, hi;
		double[] lower = Arrays.copyOfRange(dft_data, 0, 4);
		double[] higher = Arrays.copyOfRange(dft_data, 4, 8);

		low = DecoderUtil.maxIndex(lower);
		hi = DecoderUtil.maxIndex(higher);

		if (low == 0) { // low = 697
			if (hi == 0) { // High = 1209
				out = '1';
			} else if (hi == 1) { // high = 1336
				out = '2';
			} else if (hi == 2) { // high = 1477
				out = '3';
			} else if (hi == 3) { // high = 1633
				out = 'A';
			} else
				throw new DTMFDecoderException("Something went terribly wrong!");

		} else if (low == 1) { // low = 770
			if (hi == 0) { // high = 1209
				out = '4';
			} else if (hi == 1) { // high = 1336
				out = '5';
			} else if (hi == 2) { // high = 1477
				out = '6';
			} else if (hi == 3) { // high = 1633
				out = 'B';
			} else
				throw new DTMFDecoderException("Something went terribly wrong!");

		} else if (low == 2) { // low = 852
			if (hi == 0) { // high = 1209
				out = '7';
			} else if (hi == 1) { // high = 1336
				out = '8';
			} else if (hi == 2) { // high = 1477
				out = '9';
			} else if (hi == 3) { // high = 1633
				out = 'C';
			} else
				throw new DTMFDecoderException("Something went terribly wrong!");

		} else if (low == 3) { // low = 941
			if (hi == 0) { // high = 1209
				out = '*';
			} else if (hi == 1) { // high = 1336
				out = '0';
			} else if (hi == 2) { // high = 1477
				out = '#';
			} else if (hi == 3) { // high = 1633
				out = 'D';
			} else
				throw new DTMFDecoderException("Something went terribly wrong!");
		} else
			throw new DTMFDecoderException("Something went terribly wrong!");
		return out;
	}

	public static double[] toDoubleArray(byte[] byteArray)
	{
		double[] doubles = new double[byteArray.length / 2];
		for (int i = 0, j = 0; i != doubles.length; ++i, j += 2) {
			doubles[i] = (double)( (byteArray[j  ] & 0xff) | 
								   ((byteArray[j+1] & 0xff) <<  8));
		}
		return doubles;
	}

	// This works on a byte array and breaks down the frame into better sized chunks
	public char decodeNextFrameMono(byte[] buffer)
	{
		HashMap<Character, Integer> votes = new HashMap<Character, Integer>();
        int length = buffer.length;
        int chunkSize = this.getFrameSize();
        for (int i = 0; i < length; i += chunkSize) {
            // The 'end' might exceed the array length.
            int end = Math.min(length, i + chunkSize);

            // Getting the subarray.
            byte[] chunk = Arrays.copyOfRange(buffer, i, end);
            try
            {
                char dtmfChar = this.decodeNextFrameMono(toDoubleArray(chunk));
                if (votes.containsKey(dtmfChar))
                {
                    votes.put(dtmfChar, votes.get(dtmfChar) +1);
                } else {
                    votes.put(dtmfChar, 1);
                }
            } catch (Exception xe) {}
        }
		int votesSize = votes.size();
		if (votesSize == 2)
		{
			//chances are if we detected a tone and silence, there was a tone.
			if (votes.containsKey('_'))
			{
				votes.remove('_');
			}
		}
		//System.err.print(String.valueOf(votesSize) +  "  " + votes.entrySet().stream().map((entry) -> { return entry.getKey().toString() + ":" + entry.getValue().toString(); }).collect(Collectors.joining(", ")));
		Map.Entry<Character, Integer> maxEntry = votes.entrySet().stream().max(Map.Entry.comparingByValue()).orElse(null);
        char dtmfChar = '_';
        if (maxEntry != null)
            dtmfChar = maxEntry.getKey();
		if (maxEntry.getValue() < 2)
		{
			dtmfChar = '_';
		}
		//System.err.println("  =  " + dtmfChar);
		return dtmfChar;
	}
	
	/**
	 * Method to decode the next frame in a buffer of a mono channeled wav file
	 * 
	 * @return the decoded DTMF character
	 * @throws AudioFileException
	 * @throws IOException
	 * @throws WavFileException
	 * @throws DTMFDecoderException
	 */
	private char decodeNextFrameMono(double[] buffer) throws DTMFDecoderException, IOException {
		int bufferSize = (int) buffer.length;
		double[] tempBuffer11 = new double[bufferSize];
		double[] tempBuffer21 = new double[bufferSize];

		double[] frame;
		if (goertzel) {
			frame = DecoderUtil.concatenateAll(tempBuffer21, tempBuffer11, buffer);
			tempBuffer21 = tempBuffer11;
			tempBuffer11 = buffer;
		} else {
			// slice off the extra bit to make the framesize a power of 2
			int slice = buffer.length + tempBuffer11.length + tempBuffer21.length - frameSize;
			double[] sliced = Arrays.copyOfRange(buffer, 0, buffer.length - slice);

			frame = DecoderUtil.concatenateAll(tempBuffer21, tempBuffer11, sliced);
			tempBuffer21 = tempBuffer11;
			tempBuffer11 = buffer;
		}

		char out;
		// check if the power of the signal is high enough to be accepted.
		if (DecoderUtil.signalPower(frame) < CUT_OFF_POWER)
			return '_';

		if (goertzel) {
			// transform frame and return frequency spectrum information
			double[] dft_data = DTMFUtil.transformFrameG(frame, (int) audio.getSampleRate());

			// check if the frame has too much noise
			if (isNoisyG(dft_data))
				return '_';

			out = DTMFUtil.getRawChar(dft_data);
			return out;

		} else {
			// transform frame and return frequency spectrum information
			double[] power_spectrum = DTMFUtil.transformFrameFFT(frame, (int) audio.getSampleRate());

			// filter out the 8 DTMF frequencies from the power spectrum
			double[] dft_data = filterFrame(power_spectrum);

			// check if the frame has too much noise
			if (isNoisy(dft_data, power_spectrum))
				return '_';

			out = DTMFUtil.getRawChar(dft_data);
			return out;
		}
	}

	/**
	 * Method to check if the given audio file has been decoded.
	 */
	public boolean isDecoded() {
		return decoded;
	}

	/**
	 * Method to get the number of channels in the audio files being decoded
	 * 
	 * @return
	 */
	public int getChannelCount() {
		return audio.getNumChannels();
	}

	/**
	 * Method to generate the DTMF tone.
	 * 
	 * @return True if generation was successful
	 * @throws DTMFDecoderException
	 */
	public boolean generate() throws DTMFDecoderException {
		if (!generate)
			throw new DTMFDecoderException(
					"The object was not instantiated in the generation mode. Plese use the correct constructor.");

		ArrayList<Double> outSamples = new ArrayList<Double>();

		// calculate length (number of samples) of the tones and pauses
		int toneLen = (int) Math.floor((outToneDurr * outFs) / 1000.0);
		int pauseLen = (int) Math.floor((outPauseDurr * outFs) / 1000.0);

		// Add a pause at beginning of the file
		addPause(outSamples, pauseLen);

		// add the tones
		for (int i = 0; i < outChars.length; i++) {
			// add tone samples
			addTone(outSamples, outChars[i], toneLen);
			// add pause samples
			addPause(outSamples, pauseLen);
		}
		// Add a pause at the end of the file
		addPause(outSamples, pauseLen);

		generatedSeq = new double[outSamples.size()];
		for (int i = 0; i < generatedSeq.length; i++)
			generatedSeq[i] = outSamples.get(i);
		generated = true;
		return true;
	}

	/**
	 * Method to generate samples representing a dtmf tone
	 * 
	 * @param samples
	 *            array of samples to add the generated samples to.
	 * @param c
	 *            DTMF character to generate.
	 * @param toneLen
	 *            Number of samples to generate.
	 * @throws DTMFDecoderException
	 *             If the given character is not a dtmf character.
	 */
	private void addTone(ArrayList<Double> samples, char c, int toneLen) throws DTMFDecoderException {
		double[] f = getFreqs(c);
		for (double s = 0; s < toneLen; s++) {
			double lo = Math.sin(2.0 * Math.PI * f[0] * s / outFs);
			double hi = Math.sin(2.0 * Math.PI * f[1] * s / outFs);
			samples.add((hi + lo) / 2.0);
			// samples.add(hi);
		}
	}

	/**
	 * Method get the DTMF lower and upper frequencies.
	 * 
	 * @param c
	 *            DTMF character
	 * @return DTMF Frequencies to use to generate the tone.
	 * @throws DTMFDecoderException
	 *             If the given character is not a DTMF character.
	 */
	private double[] getFreqs(char c) throws DTMFDecoderException {
		double[] out = new double[2];

		if (c == '0') {
			out[0] = 941;
			out[1] = 1336;
		} else if (c == '1') {
			out[0] = 697;
			out[1] = 1209;
		} else if (c == '2') {
			out[0] = 697;
			out[1] = 1336;
		} else if (c == '3') {
			out[0] = 697;
			out[1] = 1477;
		} else if (c == '4') {
			out[0] = 770;
			out[1] = 1209;
		} else if (c == '5') {
			out[0] = 770;
			out[1] = 1336;
		} else if (c == '6') {
			out[0] = 770;
			out[1] = 1477;
		} else if (c == '7') {
			out[0] = 852;
			out[1] = 1209;
		} else if (c == '8') {
			out[0] = 852;
			out[1] = 1336;
		} else if (c == '9') {
			out[0] = 852;
			out[1] = 1477;
		} else if (c == 'A' || c == 'a') {
			out[0] = 697;
			out[1] = 1633;
		} else if (c == 'B' || c == 'b') {
			out[0] = 770;
			out[1] = 1633;
		} else if (c == 'C' || c == 'c') {
			out[0] = 852;
			out[1] = 1633;
		} else if (c == 'D' || c == 'd') {
			out[0] = 941;
			out[1] = 1633;
		} else
			throw new DTMFDecoderException("\"" + c + "\" is not a DTMF Character.");

		return out;
	}

	/**
	 * Method to add samples that represent a pause to the output
	 * 
	 * @param samples
	 *            Array of samples to add to.
	 * @param pauseLen
	 *            Number of samples to add.
	 */
	private void addPause(ArrayList<Double> samples, int pauseLen) {
		for (int s = 0; s < pauseLen; s++)
			samples.add(0.0);
	}

	/**
	 * Write the generated sequenec to a wav file.
	 * 
	 * @throws WavFileException
	 * @throws IOException
	 */
	/*
	public void export() throws IOException, WavFileException {
		FileUtil.writeWavFile(outFile, generatedSeq, outFs);
	}*/

	/**
	 * Get the samples array of the DTMF sequence of tones.
	 * 
	 * @return array with the samples of the dtmf sequence that has been
	 *         generated.
	 * @throws DTMFDecoderException
	 *             If the samples have no been generated yet.
	 */
	public double[] getGeneratedSequence() throws DTMFDecoderException {

		if (generated)
			return generatedSeq;
		else
			throw new DTMFDecoderException("Samples have not been generated yet. Please run generate() first.");
	}
}
