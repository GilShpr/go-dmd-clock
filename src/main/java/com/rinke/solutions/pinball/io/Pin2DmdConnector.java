package com.rinke.solutions.pinball.io;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.usb4java.Context;
import org.usb4java.DeviceHandle;

import com.rinke.solutions.pinball.model.Frame;
import com.rinke.solutions.pinball.model.PalMapping;
import com.rinke.solutions.pinball.model.Palette;
import com.rinke.solutions.pinball.model.Plane;

public abstract class Pin2DmdConnector {

	public static enum UsbCmd {
		RESET(0), SAVE_CONFIG(1), SWITCH_DEVICEMODE(2), SWITCH_PALETTE(3),  UPLOAD_PALETTE(4), UPLOAD_MAPPING(5),
		UPLOAD_SMARTDMD_SIG(6), RESET_SETTINGS(7), SET_DISPLAY_TIMING(8), WRITE_FILE(9), WRITE_FILE_EX(10), SEND_SETTINGS(16),
		UPLOAD_MASK(17), DELETE_LICENSE(0xFD), RECEIVE_LICENSE(0xFE), DISPLAY_UID(0xFF);
		
		UsbCmd(int cmd) {
			this.cmd = (byte)cmd;
		}
		byte cmd;
	};
	
	public static class ConnectionHandle {
		
	}
	
	protected String address;
	
    public Pin2DmdConnector(String address) {
		super();
		this.address = address;
	}

	protected byte[] buildBuffer(UsbCmd usbCmd) {
        byte[] res = new byte[2052];
        res[0] = (byte)0x81;
        res[1] = (byte)0xc3;
        res[2] = (byte)0xe7; // used for small buffer 2052
        res[3] = (byte)0xFF; // do config
        res[4] = usbCmd.cmd;
        return res;
    }

    protected byte[] buildFrameBuffer() {
        byte[] res = new byte[2052];
        res[0] = (byte)0x81;
        res[1] = (byte)0xc3;
        res[2] = (byte)0xe7;
        res[3] = (byte)0x00;
        return res;
    }

    protected byte[] fromMapping(PalMapping palMapping) {
    	byte[] res = buildBuffer(UsbCmd.UPLOAD_MAPPING);
        int j = 5;
        for(int i = 0; i < palMapping.digest.length; i++)
            res[j++] = palMapping.digest[i];
        res[j++] = (byte) palMapping.palIndex;
        res[j++] = (byte) (palMapping.durationInFrames / 256);
        res[j++] = (byte) (palMapping.durationInFrames & 0xFF);
        return res;
    }

    protected byte[] fromPalette(Palette palette) {
    	byte[] res = buildBuffer(UsbCmd.UPLOAD_PALETTE);
        //palette.writeTo(os);
        res[5] = (byte) palette.index;
        res[6] = (byte) palette.type.ordinal();// 6: type / default
        // 7 palette data
        int j = 7;
        for( int i =0; i < palette.colors.length;i++) {
            res[j++] = (byte) palette.colors[i].red;
            res[j++] = (byte) palette.colors[i].green;
            res[j++] = (byte) palette.colors[i].blue;
        }
        
        return res;
    }

    /* (non-Javadoc)
	 * @see com.rinke.solutions.pinball.io.Pin2DmdConnector#sendCmd(com.rinke.solutions.pinball.io.UsbTool.UsbCmd)
	 */
    public void sendCmd(UsbCmd cmd) {
    	byte[] res = buildBuffer(cmd);
    	bulk(res);
    }
    
    /* (non-Javadoc)
	 * @see com.rinke.solutions.pinball.io.Pin2DmdConnector#upload(java.util.List)
	 */
    public void upload(List<PalMapping> palMapppings) { 
        for (PalMapping palMapping : palMapppings) {
            byte[] bytes = fromMapping(palMapping);
            bulk(bytes);
        }
    }
    
    /* (non-Javadoc)
	 * @see com.rinke.solutions.pinball.io.Pin2DmdConnector#installLicense(java.lang.String)
	 */
    public void installLicense(String keyFile) {
    	byte[] res = buildBuffer(UsbCmd.RECEIVE_LICENSE);
    	ConnectionHandle usb = null;
    	try (FileInputStream stream = new FileInputStream(keyFile)){
			stream.read(res, 5, 68);
			usb = connect(null);
			send(res, usb);
			Thread.sleep(100);
			receive(usb,64);
			Thread.sleep(1000);
			res = buildBuffer(UsbCmd.RESET);
			send(res,usb);
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException("problems installing license", e);
		} finally {
			release(usb);
		}
    }
    
	public void upload(Palette palette) { 
		upload(palette, null);
    }
    
    /* (non-Javadoc)
	 * @see com.rinke.solutions.pinball.io.Pin2DmdConnector#transferFile(java.lang.String, java.io.InputStream)
	 */
	public void transferFile(String filename, InputStream is) {
    	byte[] data = buildBuffer(UsbCmd.WRITE_FILE_EX);
    	data[5] = (byte) 0;
    	String sdname = "0:/"+filename;
    	buildBytes(data, sdname);
    	ConnectionHandle usb = connect(this.address);    
        try {
        	send(data, usb);
        	doHandShake(usb);
        	byte[] buffer = new byte[512];
        	int read;
        	while( (read = is.read(buffer)) > 0 ){
        		data = buildBuffer(UsbCmd.WRITE_FILE_EX);
        		data[5] = (byte) 1;
        		data[6] = (byte) (read >> 8);
        		data[7] = (byte) (read & 0xFF);
        		send(data, usb);
        		doHandShake(usb);
        	}
        	data = buildBuffer(UsbCmd.WRITE_FILE_EX);
    		data[5] = (byte) 0xFF;
    		send(data, usb);
    		doHandShake(usb);
        } catch (IOException e) {
        	throw new RuntimeException(e);
		} finally {
        	release(usb);
        }
    	
    }

	private void doHandShake(ConnectionHandle usb) {
		byte[] res = receive(usb,64);
		if( res[0] != 0) throw new RuntimeException("handshake error");
	}

	private void buildBytes(byte[] res, String sdname) {
		try {
			byte[] namebytes = sdname.getBytes("ASCII");
			int i = 6;
			for (byte b : namebytes) {
				res[i++] = b;
			}
			res[i] = 0;
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}
	

    
    protected abstract byte[] receive(ConnectionHandle usb, int len);

	protected abstract void send(byte[] res, ConnectionHandle usb);

	/* (non-Javadoc)
	 * @see com.rinke.solutions.pinball.io.Pin2DmdConnector#sendFrame(com.rinke.solutions.pinball.model.Frame, org.apache.commons.lang3.tuple.Pair)
	 */
    public void sendFrame( Frame frame, ConnectionHandle usb ) {
    	//LOG.info("sending frame to device: {}", frame);
    	byte[] buffer = buildFrameBuffer();
    	int i = 0;
    	if( frame.planes.size() == 2 ) {
    		System.arraycopy(Frame.transform(frame.planes.get(1).plane), 0, buffer, 4+1*512, 512);
    		System.arraycopy(Frame.transform(frame.planes.get(1).plane), 0, buffer, 4+2*512, 512);
    		byte[] planeOr = new byte[512];
    		byte[] planeAnd = new byte[512];
    		byte[] plane0 = frame.planes.get(0).plane;
    		byte[] plane1 = frame.planes.get(1).plane;
    		
    		for (int j = 0; j < plane0.length; j++) {
				planeOr[j] =  (byte) (plane0[j] | plane1[j]);
				planeAnd[j] =  (byte) (plane0[j] & plane1[j]);
			}
    		System.arraycopy(Frame.transform(planeOr), 0, buffer, 4+0*512, 512);
    		System.arraycopy(Frame.transform(planeAnd), 0, buffer, 4+3*512, 512);
    	} else {
        	for( Plane p : frame.planes) {
        		System.arraycopy(Frame.transform(p.plane), 0, buffer, 4+i*512, 512);
        		if( i++ > 3 ) break;
        	}
    	}
    	send(buffer, usb);
    }
    
    /* (non-Javadoc)
	 * @see com.rinke.solutions.pinball.io.Pin2DmdConnector#switchToPal(int)
	 */
    public void switchToPal( int standardPalNumber ) {
    	byte[] res = buildBuffer(UsbCmd.SWITCH_PALETTE);
    	res[5] = (byte) standardPalNumber;
    	bulk(res);
    }

    /* (non-Javadoc)
	 * @see com.rinke.solutions.pinball.io.Pin2DmdConnector#switchToMode(int)
	 */
    public void switchToMode( int deviceMode ) {
    	byte[] res = buildBuffer(UsbCmd.SWITCH_DEVICEMODE);
    	res[5] = (byte) deviceMode;
    	bulk(res);
    }
    


	public abstract ConnectionHandle connect(String address);
	
	public abstract void release(ConnectionHandle usb);

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public void bulk(byte[] data) {
	    ConnectionHandle usb = connect(this.address);      
	    try {
	    	send(data, usb);
	    } finally {
	    	release(usb);
	    }
	}

	public void upload(Palette palette, ConnectionHandle handle) { 
	    byte[] bytes = fromPalette(palette);
	    if( handle == null ) {
	    	bulk(bytes);
	    } else {
	    	send(bytes, handle);
	    }
		
	}

}