package org.openstatic.serial;

import java.util.HashMap;
import java.util.Iterator;

import org.json.JSONArray;
import org.openstatic.kiss.JavaKISSMain;

import com.fazecast.jSerialComm.*;

public class SerialSystem
{
    private HashMap<String, SerialPort> ports;

    public SerialSystem()
    {
		this.refresh();
    }

    public void refresh()
    {
        this.ports = new HashMap<String, SerialPort>();
        SerialPort[] portsArray = SerialPort.getCommPorts();
        for(int i = 0; i < portsArray.length; i++)
        {
            String portName = portsArray[i].getSystemPortName();
            this.ports.put(portName, portsArray[i]);
        }
    }

    public void setRTS(String serialPort, boolean value)
    {
        SerialPort port = this.ports.get(serialPort);
        if (port != null)
        {
            if (!port.isOpen())
                port.openPort();
            if (value)
            {
                port.setRTS();
                JavaKISSMain.mainLog("[RTS SET] " + serialPort);
            } else {
                port.clearRTS();
                port.closePort();
                JavaKISSMain.mainLog("[RTS CLEARED] " + serialPort);
            }
        } else {
            JavaKISSMain.mainLog("[RTS ERROR] Couldnt Find " + serialPort);
        }
    }

    public void setDTR(String serialPort, boolean value)
    {
        SerialPort port = this.ports.get(serialPort);
        if (port != null)
        {
            if (!port.isOpen())
                port.openPort();
            if (value)
            {
                port.setDTR();
                JavaKISSMain.mainLog("[DTR SET] " + serialPort);
            } else {
                port.clearDTR();
                port.closePort();
                JavaKISSMain.mainLog("[DTR CLEARED] " + serialPort);
            }
        } else {
            JavaKISSMain.mainLog("[DTR ERROR] Couldnt Find " + serialPort);
        }
    }

    public JSONArray getSerialPorts()
    {
        JSONArray ra = new JSONArray();
        Iterator<SerialPort> portsIterator = this.ports.values().iterator();
        while(portsIterator.hasNext())
        {
            SerialPort port = portsIterator.next();
            ra.put(port.getSystemPortName());
        }
        return ra;
    }
}