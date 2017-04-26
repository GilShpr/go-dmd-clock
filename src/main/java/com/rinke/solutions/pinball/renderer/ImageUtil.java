package com.rinke.solutions.pinball.renderer;

import java.awt.image.BufferedImage;
import java.awt.image.DirectColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;

import lombok.extern.slf4j.Slf4j;

import com.rinke.solutions.pinball.DMD;
import com.rinke.solutions.pinball.PinDmdEditor;
import com.rinke.solutions.pinball.model.Frame;
import com.rinke.solutions.pinball.model.Palette;
import com.rinke.solutions.pinball.model.Plane;
import com.rinke.solutions.pinball.model.RGB;


@Slf4j
public class ImageUtil {
	
	static int lowThreshold = 50;
	static int midThreshold = 120;
	static int highThreshold = 200;
	
	/**
	 * Converts an swt based image into an AWT <code>BufferedImage</code>. This
	 * will always return a <code>BufferedImage</code> that is of type
	 * <code>BufferedImage.TYPE_INT_ARGB</code> regardless of the type of swt
	 * image that is passed into the method.
	 * 
	 * @param srcImage
	 *            the {@link org.eclipse.swt.graphics.Image} to be converted to
	 *            a <code>BufferedImage</code>
	 * @return a <code>BufferedImage</code> that represents the same image data
	 *         as the swt <code>Image</code>
	 */
	public static BufferedImage convert(Image srcImage) {

		ImageData imageData = srcImage.getImageData();
		int width = imageData.width;
		int height = imageData.height;
		ImageData maskData = null;
		int alpha[] = new int[1];

		if (imageData.alphaData == null)
			maskData = imageData.getTransparencyMask();

		// now we should have the image data for the bitmap, decompressed in
		// imageData[0].data.
		// Convert that to a Buffered Image.
		BufferedImage image = new BufferedImage(imageData.width,
				imageData.height, BufferedImage.TYPE_INT_ARGB);

		WritableRaster alphaRaster = image.getAlphaRaster();

		// loop over the imagedata and set each pixel in the BufferedImage to
		// the appropriate color.
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				org.eclipse.swt.graphics.RGB color = imageData.palette.getRGB(imageData.getPixel(x, y));
				image.setRGB(x, y, new java.awt.Color(color.red, color.green,
						color.blue).getRGB());

				// check for alpha channel
				if (alphaRaster != null) {
					if (imageData.alphaData != null) {
						alpha[0] = imageData.getAlpha(x, y);
						alphaRaster.setPixel(x, y, alpha);
					} else {
						// check for transparency mask
						if (maskData != null) {
							alpha[0] = maskData.getPixel(x, y) == 0 ? 0 : 255;
							alphaRaster.setPixel(x, y, alpha);
						}
					}
				}
			}
		}

		return image;
	}

	
	public static ImageData convertToSWT(BufferedImage bufferedImage) {
		if (bufferedImage.getColorModel() instanceof DirectColorModel) {
			DirectColorModel colorModel = (DirectColorModel) bufferedImage
					.getColorModel();
			PaletteData palette = new PaletteData(colorModel.getRedMask(),
					colorModel.getGreenMask(), colorModel.getBlueMask());
			ImageData data = new ImageData(bufferedImage.getWidth(),
					bufferedImage.getHeight(), colorModel.getPixelSize(),
					palette);
			
			WritableRaster raster = bufferedImage.getRaster();
			int[] pixelArray = new int[4];
			for (int y = 0; y < data.height; y++) {
				for (int x = 0; x < data.width; x++) {
					raster.getPixel(x, y, pixelArray);
					int pixel = palette.getPixel(new org.eclipse.swt.graphics.RGB(pixelArray[0],
							pixelArray[1], pixelArray[2]));
					data.setPixel(x, y, pixel);
					data.setAlpha(x, y, pixelArray[3]);
				}
			}
			return data;
		} else if (bufferedImage.getColorModel() instanceof IndexColorModel) {
			IndexColorModel colorModel = (IndexColorModel) bufferedImage
					.getColorModel();
			int size = colorModel.getMapSize();
			byte[] reds = new byte[size];
			byte[] greens = new byte[size];
			byte[] blues = new byte[size];
			colorModel.getReds(reds);
			colorModel.getGreens(greens);
			colorModel.getBlues(blues);
			org.eclipse.swt.graphics.RGB[] rgbs = new org.eclipse.swt.graphics.RGB[size];
			for (int i = 0; i < rgbs.length; i++) {
				rgbs[i] = new org.eclipse.swt.graphics.RGB(reds[i] & 0xFF, greens[i] & 0xFF,
						blues[i] & 0xFF);
			}
			PaletteData palette = new PaletteData(rgbs);
			ImageData data = new ImageData(bufferedImage.getWidth(),
					bufferedImage.getHeight(), colorModel.getPixelSize(),
					palette);
			data.transparentPixel = colorModel.getTransparentPixel();
			WritableRaster raster = bufferedImage.getRaster();
			int[] pixelArray = new int[1];
			for (int y = 0; y < data.height; y++) {
				for (int x = 0; x < data.width; x++) {
					raster.getPixel(x, y, pixelArray);
					data.setPixel(x, y, pixelArray[0]);
				}
			}
			return data;
		}
		return null;
	}

	public static Frame convertToFrameWithPalette(BufferedImage dmdImage, DMD dmd, Palette palette) {
		Frame res = new Frame();
		int noOfPlanes = 1;
		while( palette.numberOfColors > (1<<noOfPlanes)) noOfPlanes++;
		for( int j = 0; j < noOfPlanes ; j++) {
			res.planes.add(new Plane((byte)j, new byte[dmd.getPlaneSizeInByte()]));
		}
		
		for (int x = 0; x < dmd.getWidth(); x++) {
			for (int y = 0; y < dmd.getHeight(); y++) {

				int rgb = dmdImage.getRGB(x, y);
				int idx = findColorIndex(rgb, palette);
				
				for( int j = 0; j < noOfPlanes ; j++) {
					if( (idx & (1<<j)) != 0)
						res.planes.get(j).plane[y * dmd.getBytesPerRow() + x / 8] |= (PinDmdEditor.DMD_WIDTH >> (x % 8));
				}

			}
		}
		return res;
	}
	
	private static int findColorIndex(int rgb, Palette palette) {
		for (int i = 0; i < palette.colors.length; i++) {
			RGB p = palette.colors[i];
			if( p.red == ((rgb >> 16) & 0xFF) && p.green == ((rgb >> 8) & 0xFF) && p.blue == (rgb & 0xFF) ) {
				return i;
			}
			
		}
		return 0;
	}


	
	public static Frame convertToFrame(BufferedImage dmdImage, int w, int h) {
		Frame res = new Frame();
		for( int j = 0; j < 15 ; j++) {
			res.planes.add(new Plane((byte)j, new byte[w*h/8]));
		}
		
		for (int x = 0; x < w; x++) {
			for (int y = 0; y < h; y++) {

				int rgb = dmdImage.getRGB(x, y);
				
				// reduce color depth to 15 bit
				int nrgb = ( rgb >> 3 ) & 0x1F;
				nrgb |= ( ( rgb >> 11 ) & 0x1F ) << 5;
				nrgb |= ( ( rgb >> 19 ) & 0x1F ) << 10;
				
				for( int j = 0; j < 15 ; j++) {
					if( (nrgb & (1<<j)) != 0)
						res.planes.get(j).plane[y * (w/8) + x / 8] |= (w >> (x % 8));
				}

			}
		}
		return res;
	}

	public static Frame convertTo4Color(BufferedImage master, int w, int h) {
		Map<Integer, Integer> grayCounts = new HashMap<>();
		int frameSizeInByte = w*h/8;
		int bytesPerRow = w/8;
		byte[] f1 = new byte[frameSizeInByte];
		byte[] f2 = new byte[frameSizeInByte];

		Frame res = new Frame(f1, f2);

		for (int x = 0; x < w; x++) {
			for (int y = 0; y < h; y++) {
				int rgb = master.getRGB(x, y);
				int gray = (int) ((0.299f * (rgb >> 24)) + 0.587f
						* ((rgb >> 16) & 0xFF) + 0.114f * ((rgb >> 8) & 0xFF));
				// < 20 -> schwarz
				// >= 20 && < 100 ->low						
				// > 100 <180 - mid
				// > 180 - high
				// ironman 20,100,125
				// WCS 20,50,100
				if (gray > lowThreshold && gray < midThreshold) {
					// set f1
					f1[y*bytesPerRow + x / 8] |= (w >> (x % 8));
				} else if (gray >= midThreshold && gray < highThreshold) {
					f2[y*bytesPerRow + x / 8] |= (w >> (x % 8));
				} else if (gray >= highThreshold ) {
					f1[y*bytesPerRow + x / 8] |= (w >> (x % 8));
					f2[y*bytesPerRow + x / 8] |= (w >> (x % 8));
				}
				if (!grayCounts.containsKey(gray)) {
					grayCounts.put(gray, 0);
				}

				grayCounts.put(gray, grayCounts.get(gray) + 1);
			}
		}
		log.debug("----");
		for (int v : grayCounts.keySet()) {
			log.debug("Grauwert " + v + " = "
					+ grayCounts.get(v));
		}
		return res;
	}

}