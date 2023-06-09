package org.openstatic.kiss;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class KissProcessor
{
    private final int KISS_TX_FRAME_MAX_SIZE = 798;

    private final byte KISS_FEND = (byte)0xc0;
    private final byte KISS_FESC = (byte)0xdb;
    private final byte KISS_TFEND = (byte)0xdc;
    private final byte KISS_TFESC = (byte)0xdd;

    public final byte KISS_CMD_DATA = (byte)0x00;
    public final byte KISS_CMD_TX_DELAY = (byte)0x01;
    public final byte KISS_CMD_P = (byte)0x02;
    public final byte KISS_CMD_SLOT_TIME = (byte)0x03;
    public final byte KISS_CMD_DUPLEX = (byte)0x05;
    public final byte KISS_CMD_NOCMD = (byte)0x80;

    private final byte KISS_MODEM_STREAMING = (byte)0x20;   // This is the streaming modem ID.
    private final byte KISS_MODEM_BERT = (byte)0x30;   // This is the streaming modem ID.

    private enum KissState {
        VOID,
        GET_CMD,
        GET_DATA,
        ESCAPE
    };

    private KissState _kissState = KissState.VOID;
    private byte _kissCmd = KISS_CMD_NOCMD;

    private final byte _tncCsmaPersistence = 0x3f;          // 63 is recommended by M17 KISS Spec.
    private final byte _tncCsmaSlotTime = (byte) 0x04;      // Required by M17 KISS Spec.
    private final byte _tncTxDelay;                         // This is the only real tunable.
    private final byte _tncDuplex;                          // Controls BCL; defaults to BCL off.

    private final byte[] _outputKissBuffer;
    private final byte[] _inputKissBuffer;

    private final KISSClient _callback;

    private int _outputKissBufferPos;
    private int _inputKissBufferPos;

    public KissProcessor(KISSClient callback, byte txDelay)
    {
        _callback = callback;
        _outputKissBuffer = new byte[KISS_TX_FRAME_MAX_SIZE];
        _inputKissBuffer = new byte[100 * KISS_TX_FRAME_MAX_SIZE];
        _tncTxDelay = txDelay;
        _tncDuplex = 1;
        _outputKissBufferPos = 0;
        _inputKissBufferPos = 0;
    }

    public KissProcessor(KISSClient callback, byte txDelay, byte duplex) 
    {
        _callback = callback;
        _outputKissBuffer = new byte[KISS_TX_FRAME_MAX_SIZE];
        _inputKissBuffer = new byte[100 * KISS_TX_FRAME_MAX_SIZE];
        _tncTxDelay = txDelay;
        _tncDuplex = duplex;
        _outputKissBufferPos = 0;
        _inputKissBufferPos = 0;
    }

    public void send(byte [] frame) throws IOException 
    {
        ByteArrayOutputStream output = new ByteArrayOutputStream(frame.length * 2);
        output.write(KISS_FEND);
        output.write(KISS_MODEM_STREAMING);
        escape(frame, output);
        output.write(KISS_FEND);
        _callback.onKPSend(output.toByteArray());
    }

    public void receive(byte[] data) 
    {
        for (byte b : data) {
            switch (_kissState) {
                case VOID:
                    if (b == KISS_FEND) {
                        _kissCmd = KISS_CMD_NOCMD;
                        _kissState = KissState.GET_CMD;
                        //System.err.println("Kiss State GET_CMD");
                    }
                    break;
                case GET_CMD:
                    if ((b & 7) == KISS_CMD_DATA) {
                        _kissCmd = b;
                        //System.err.println("KISS COMMAND: " + String.valueOf((int)_kissCmd));
                        _kissState = KissState.GET_DATA;
                        //System.err.println("Kiss State GET_DATA");
                    } else if (b != KISS_FEND) {
                        resetState();
                    }
                    break;
                case GET_DATA:
                    if (b == KISS_FESC) {
                        _kissState = KissState.ESCAPE;
                        //System.err.println("Kiss State ESCAPE");
                    } else if (b == KISS_FEND) {
                        if (_kissCmd == KISS_CMD_DATA) {
                            //System.err.println("from " + String.valueOf(_inputKissBufferDataStart) + " to " + String.valueOf(_inputKissBufferPos));
                            _callback.onKPReceive(Arrays.copyOfRange(_inputKissBuffer, 0, _inputKissBufferPos));
                        }
                        resetState();
                    } else {
                        receiveFrameByte(b);
                    }
                    break;
                case ESCAPE:
                    if (b == KISS_TFEND) {
                        receiveFrameByte(KISS_FEND);
                        _kissState = KissState.GET_DATA;
                        //System.err.println("Kiss State GET_DATA");
                    } else if (b == KISS_TFESC) {
                        receiveFrameByte(KISS_FESC);
                        _kissState = KissState.GET_DATA;
                        //System.err.println("Kiss State GET_DATA");
                    } else {
                        resetState();
                    }
                    break;
                default:
                    break;
            }
        }
    }

    public void flush() throws IOException
    {
        completeKissPacket();
    }

    public void sendKissByte(byte b) 
    {
        _outputKissBuffer[_outputKissBufferPos++] = b;
    }

    private void receiveFrameByte(byte b) 
    {
        _inputKissBuffer[_inputKissBufferPos++] = b;
        if (_inputKissBufferPos >= _inputKissBuffer.length) {
            //Log.e(TAG, "Input KISS buffer overflow, discarding frame");
            resetState();
        }
    }

    private void resetState() 
    {
        _kissCmd = KISS_CMD_NOCMD;
        _kissState = KissState.VOID;
        //System.err.println("Kiss State VOID");
        _inputKissBufferPos = 0;
    }

    public void startKissPacket(byte commandCode) throws IOException 
    {
        sendKissByte(KISS_FEND);
        sendKissByte(commandCode);
    }

    public void completeKissPacket() throws IOException 
    {
        if (_outputKissBufferPos > 0) {
            sendKissByte(KISS_FEND);
            _callback.onKPSend(Arrays.copyOf(_outputKissBuffer, _outputKissBufferPos));
            _outputKissBufferPos = 0;
        }
    }

    private void escape(byte[] inputBuffer, ByteArrayOutputStream output)
    {
        for (byte b : inputBuffer) {
            switch (b) {
                case KISS_FEND:
                    output.write(KISS_FESC);
                    output.write(KISS_TFEND);
                    break;
                case KISS_FESC:
                    output.write(KISS_FESC);
                    output.write(KISS_TFESC);
                    break;
                default:
                    output.write(b);
                    break;
            }
        }
    }
}