
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

public class DUtil {

	// main methods start
	public static void main(String[] args) throws Exception {

		if (args.length == 0) {
			showHelp();
		}

		new DUtil().run(args);
	}

	/**
	 * This method reads arguments and invokes the respective operations
	 * 
	 * @param args
	 * @throws IOException
	 */
	private void run(String[] args) throws IOException {

		BufferedImage bi = ImageIO.read(new File(args[0]));
		final int width = bi.getWidth();
		final int height = bi.getHeight();
		boolean saveAsJPG = true;
		System.out.println("Img size w, h :: " + width + ", " + height);

		final String argStr = args[1].trim();
		final String[] argStrArr = argStr.split("::");
		for (String argStrArrVal : argStrArr) {

			final String[] arrVals = argStrArrVal.split(" ");
			final String op = arrVals[0];

			switch (op) {
			case "gam":
				generateGamutFile(bi);
				break;
			case "b":
				final double sigma = extractArgAsDoubleIfNotNull(argStrArrVal, arrVals, 1, 1.0);
				final int size = extractArgAsIntIfNotNull(argStrArrVal, arrVals, 2, 5);
				applyBlur(bi, size, sigma);
				break;
			case "cbw":
				bi = convertToBW(bi);
				break;
			case "e":
				final double lt = extractArgAsDoubleIfNotNull(argStrArrVal, arrVals, 1, 0.05);
				final double ht = extractArgAsDoubleIfNotNull(argStrArrVal, arrVals, 2, 0.09);
				bi = edgeDetect(bi, lt, ht);
				break;
			case "r":
				final int wd = extractArgAsIntIfNotNull(argStrArrVal, arrVals, 1, width / 10);
				final int htt = extractArgAsIntIfNotNull(argStrArrVal, arrVals, 2, height / 10);
				divideImg(bi, wd, htt);
				break;
			case "n":
				final int divs = extractArgAsIntIfNotNull(argStrArrVal, arrVals, 1, 10);
				bi = notan(bi, divs);
				break;
			case "su":
				BufferedImage bi2 = null;
				try {
					bi2 = ImageIO.read(new File(args[2]));
				} catch (Exception e) {
					System.out.println("Error reading second image for operator su " + e.getMessage());
					showHelp();
				}
				final int mvx = extractArgAsIntIfNotNull(argStrArrVal, arrVals, 1, 0);
				final int mvy = extractArgAsIntIfNotNull(argStrArrVal, arrVals, 2, 0);
				bi = superimpose(bi, bi2, mvx, mvy);
				break;
			case "png":
				try {
					bi = invertBW(bi);
					saveAsJPG = false;
				} catch (Exception e) {
					System.out.println("Error reading second image for operator su " + e.getMessage());
					showHelp();
				}
			}
		}

		if (saveAsJPG) {
			final File output = new File(System.getProperty("user.dir") + File.separatorChar + "img.jpg");
			ImageIO.write(bi, "JPG", output);
		} else {
			final File output = new File(System.getProperty("user.dir") + File.separatorChar + "img.png");
			ImageIO.write(bi, "PNG", output);
		}

	}

	/**
	 * This function is used to generate a gamut map of an image and write it to
	 * gmap.png file.
	 * 
	 * @param bi
	 * @throws IOException
	 */
	private void generateGamutFile(BufferedImage bi) throws IOException {

		int bw = 1024;
		int bh = 1024;
		int cx = bw / 2;
		int cy = bh / 2;
		float r = (float) (cx - 20);
		float value = 0.7f;
		float valueImg = 1.0f;

		BufferedImage gamutImg = new BufferedImage(bh, bh, BufferedImage.TYPE_INT_RGB);

		// for loops to draw hue - saturation circle
		for (int i = 0; i < bw; i++) {
			for (int j = 0; j < bh; j++) {
				float ix = (float) (i - cx);
				float iy = (float) (cy - j);

				float dist = ((ix * ix) + (iy * iy));
				dist = (float) Math.sqrt(dist);
				if (dist > r)
					continue;

				float saturation = dist / r;
				float hue = (float) Math.atan(Math.abs(iy / ix));
				hue = hue * 180.0f / (float) Math.PI;

				if (ix < 0.0f && iy > 0.0f)
					hue = 180.0f - hue;
				else if (ix < 0.0f && iy <= 0.0f)
					hue += 180.0f;
				else if (ix >= 0.0f && iy < 0.0f)
					hue = 360.0f - hue;

				hue = hue / 360.0f;

				int rgbVal = Color.getHSBColor(hue, saturation, value).getRGB();
				gamutImg.setRGB(i, j, rgbVal);
			}
		}

		// map values of image to circle and overlay as lighter value points
		for (int i = 0; i < bi.getWidth(); i++) {
			for (int j = 0; j < bi.getHeight(); j++) {
				int val = bi.getRGB(i, j);

				int red = (val >> 16) & 0xFF;
				int green = (val >> 8) & 0xFF;
				int blue = val & 0xFF;

				float[] hsv = Color.RGBtoHSB(red, green, blue, null);
				float hue = -(float) (2.0f * Math.PI * hsv[0]);
				float saturation = r * hsv[1];

				int x = cx + (int) (saturation * Math.cos(hue));
				int y = cy + (int) (saturation * Math.sin(hue));

				val = gamutImg.getRGB(x, y);
				red = (val >> 16) & 0xFF;
				green = (val >> 8) & 0xFF;
				blue = val & 0xFF;

				hsv = Color.RGBtoHSB(red, green, blue, null);
				int rgbVal = Color.getHSBColor(hsv[0], hsv[1], valueImg).getRGB();
				gamutImg.setRGB(x, y, rgbVal);
			}
		}

		File output = new File(System.getProperty("user.dir") + File.separatorChar + "gmap.png");
		ImageIO.write(gamutImg, "PNG", output);
	}

	/**
	 * Invert image to transparent background and black lines in foreground
	 * 
	 * @param bi
	 * @return
	 */
	private BufferedImage invertBW(BufferedImage bi) {

		BufferedImage out = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_INT_ARGB);

		for (int i = 0; i < bi.getWidth(); i++) {
			for (int j = 0; j < bi.getHeight(); j++) {
				int val = bi.getRGB(i, j);
				if ((val & 0x00ffffff) < 10) {
					out.setRGB(i, j, 0x00ffffff);
				} else {
					out.setRGB(i, j, 0xff000000);
				}
			}
		}

		return out;
	}

	/**
	 * Shows help
	 * 
	 */
	private static void showHelp() {
		System.out.println("Pass Arguments as: imageInput \"operator args | operator args | ...\" ");
		System.out.println("");
		System.out.println("Supported operators :");
		System.out.println("");
		System.out.println("cbw (convert to bw img)");
		System.out.println("b (blur) sigma size");
		System.out.println("e (edge detect) lowThres highThresh");
		System.out.println("n (notan) divisions");
		System.out.println("r (ruler) xdiv ydiv");
		System.out.println("gam (generate gamut mapping. Use at beginning)");
		System.out.println("png (generate png image)");
		System.out.println("su (superimpose) mvx mvy \" img2");
		System.out.println("");
		System.out.println("Eg: Canny img1 \"b 1::e 0.05 0.09::r\"");
		System.out.println("Eg: Canny img1 \"b 1::e 0.05 0.09::r::s\" img2");
		System.out.println("");

		System.exit(0);
	}
	// main methods end

	// operator method start
	/**
	 * This method is used to apply gaussian blur on the image
	 * 
	 * @param bi
	 * @param size
	 * @param sigma
	 */
	private void applyBlur(BufferedImage bi, int size, double sigma) {
		final double[][] g = gaussianKernel(size, sigma);
		convolve(bi, g);
	}

	/**
	 * This method is used to convert image to gray scale
	 * 
	 * @param img
	 * @return
	 */
	private BufferedImage convertToBW(BufferedImage img) {
		final BufferedImage gray = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
		final Graphics2D g = gray.createGraphics();
		g.drawImage(img, 0, 0, null);
		return gray;
	}

	/**
	 * This method performs edge detection with non-Max supression
	 * 
	 * @param bi
	 * @param lt
	 * @param ht
	 * @return
	 */
	private BufferedImage edgeDetect(BufferedImage bi, double lt, double ht) {
		final int[][] kx = getXFilter();
		final int[][] ky = getYFilter();

		final int bw = bi.getWidth();
		final int bh = bi.getHeight();

		final List<IntArrHolder> val = convolve(bi, kx, ky);
		return genImgForEdgeDetect(bw, bh, 1, bw - 1, 1, bh - 1, lt, ht, val);
	}

	/**
	 * This methods draws a scale on the image with given pixel widths & heights
	 * 
	 * @param bi
	 * @param wd
	 * @param ht
	 */
	private void divideImg(BufferedImage bi, int wd, int ht) {

		final int width = bi.getWidth();
		final int height = bi.getHeight();

		final Graphics2D g = bi.createGraphics();
		for (int i = 0; i < width; i += wd) {
			g.drawLine(i, 0, i, height);
		}

		for (int i = 0; i < height; i += ht) {
			g.drawLine(0, i, width, i);
		}
	}

	/**
	 * This method is used to generate notan with given value divisions in gray
	 * scale
	 * 
	 * @param bi
	 * @param divs
	 * @return
	 */
	private BufferedImage notan(BufferedImage bi, int divs) {

		bi = convertToBW(bi);

		final int range = 255 / divs;

		for (int i = 0; i < bi.getWidth(); i++) {
			for (int j = 0; j < bi.getHeight(); j++) {
				int pxVal = bi.getRGB(i, j) & 0xff;
				pxVal = ((pxVal / range) + 1) * range;
				bi.setRGB(i, j, pxVal << 16 | pxVal << 8 | pxVal);
			}
		}

		return bi;
	}

	/**
	 * SuperImpose 1st/processed image on top of 2nd one only when intensity of 1st
	 * image pixel is greater than 0.
	 * 
	 * @param bi
	 * @param bi2
	 * @param mvx
	 * @param mvy
	 * @return
	 */
	private BufferedImage superimpose(BufferedImage bi, BufferedImage bi2, int mvx, int mvy) {

		if (mvx != 0 || mvy != 0) {
			moveImg(bi, mvx, mvy);
		}

		int width1 = bi.getWidth();
		int height1 = bi.getHeight();

		int width2 = bi2.getWidth();
		int height2 = bi2.getHeight();

		int minWidth = width1 > width2 ? width2 : width1;
		int minHeight = height1 > height2 ? height2 : height1;

		bi = scaleImg(bi, minWidth, minHeight, true);
		bi2 = scaleImg(bi2, minWidth, minHeight, false);

		for (int i = 0; i < minWidth; i++) {
			for (int j = 0; j < minHeight; j++) {
				int pval = bi.getRGB(i, j) & 0xff;
				if (pval > 200)
					bi2.setRGB(i, j, Color.WHITE.getRGB());
			}
		}

		return bi2;
	}
	// operator method end

	// helper methods start
	/**
	 * This method attempts to fetch value from argument, if not found or any issue
	 * in parsing, uses the passed default value
	 * 
	 * @param arg
	 * @param arrVals
	 * @param index
	 * @param defaultValue
	 * @return
	 */
	private int extractArgAsIntIfNotNull(String arg, String[] arrVals, int index, int defaultValue) {
		int value = defaultValue;

		if (arrVals.length > index) {
			try {
				value = Integer.parseInt(arrVals[index]);
			} catch (NumberFormatException e) {
				System.out.println(e.getMessage());
				System.out.println("for " + arg);
				System.out.println("Defaulting to value " + defaultValue);
			}
		}

		return value;
	}

	/**
	 * This method attempts to fetch value from argument, if not found or any issue
	 * in parsing, uses the passed default value
	 * 
	 * @param arg
	 * @param arrVals
	 * @param index
	 * @param defaultValue
	 * @return
	 */
	private static double extractArgAsDoubleIfNotNull(String arg, String[] arrVals, int index, double defaultValue) {
		double value = defaultValue;

		if (arrVals.length > index) {
			try {
				value = Double.parseDouble(arrVals[index]);
			} catch (NumberFormatException e) {
				System.out.println(e.getMessage());
				System.out.println("for " + arg);
				System.out.println("Defaulting to value " + defaultValue);
			}
		}

		return value;
	}

	/**
	 * This method multiplies 2D double array with a constant value
	 * 
	 * @param arr
	 * @param val
	 * @return
	 */
	private double[][] multiply2DArrayWithConstant(double[][] arr, int val) {
		final double[][] output = new double[arr.length][arr.length];

		for (int i = 0; i < arr.length; i++) {
			for (int j = 0; j < arr.length; j++) {
				output[i][j] = ((double) val) * arr[i][j];
			}
		}

		return output;
	}

	/**
	 * This method multiplies 2D int array with a constant value
	 * 
	 * @param arr
	 * @param val
	 * @return
	 */
	private int[][] multiply2DArrayWithConstant(int[][] arr, int val) {
		final int[][] output = new int[arr.length][arr.length];

		for (int i = 0; i < arr.length; i++) {
			for (int j = 0; j < arr.length; j++) {
				output[i][j] = val * arr[i][j];
			}
		}

		return output;
	}

	/**
	 * Scale an image to given width & height
	 * 
	 * @param bi
	 * @param width
	 * @param height
	 * @param convertToBW
	 * @return
	 */
	private BufferedImage scaleImg(BufferedImage bi, int width, int height, boolean convertToBW) {
		Image tmp = bi.getScaledInstance(width, height, Image.SCALE_SMOOTH);
		bi = new BufferedImage(width, height, convertToBW ? BufferedImage.TYPE_BYTE_GRAY : bi.getType());
		Graphics2D g2d = bi.createGraphics();
		g2d.drawImage(tmp, 0, 0, null);
		g2d.dispose();
		return bi;
	}

	/**
	 * Move image by given amounts
	 * 
	 * @param bi
	 * @param mvx
	 * @param mvy
	 */
	private void moveImg(BufferedImage bi, int mvx, int mvy) {
		Graphics2D g2d = bi.createGraphics();
		Image img = bi.getSubimage(0, 0, bi.getWidth(), bi.getHeight());
		g2d.drawImage(img, -mvx, -mvy, null);
		g2d.dispose();
	}
	// helper methods end

	// convolution method start
	/**
	 * This method performs convolution operation. 1.It creates an output array.
	 * 2.It stores the filter array and output array into DoubleArrHolder list.
	 * 3.Invokes contribToArr to perform operation. 4.Sets values directly onto
	 * image
	 * 
	 * @param bi
	 * @param g
	 */
	private void convolve(BufferedImage bi, double[][] g) {

		final int bw = bi.getWidth();
		final int bh = bi.getHeight();

		final List<DoubleArrHolder> list = new ArrayList<>();
		final DoubleArrHolder obj = new DoubleArrHolder();
		list.add(obj);

		final double[][] arr = new double[bw][bh];
		obj.arr = arr;
		obj.filter = g;

		final int c = g.length / 2;

		for (int i = c; i < bi.getWidth() - c; i++) {
			for (int j = c; j < bi.getHeight() - c; j++) {
				int val = bi.getRGB(i, j);
				val = val & 0xff;
				contribToArr(val, i, j, list, c);
			}
		}

		for (int i = c; i < bi.getWidth() - c; i++) {
			for (int j = c; j < bi.getHeight() - c; j++) {
				int oval = (int) arr[i][j];
				oval = oval << 16 | oval << 8 | oval;
				bi.setRGB(i, j, oval);
			}
		}
	}

	/**
	 * This method performs convolution operation 1.It creates two output array's.
	 * 2.It stores the filter array and output array into IntArrHolder list.
	 * 3.Invokes contribToArrInt to perform operation and return the IntArrHolder
	 * list.
	 * 
	 * @param bi
	 * @param a1
	 * @param a2
	 * @return
	 */
	private List<IntArrHolder> convolve(BufferedImage bi, int[][] a1, int[][] a2) {

		final int bw = bi.getWidth();
		final int bh = bi.getHeight();

		final List<IntArrHolder> list = new ArrayList<>();

		final IntArrHolder obj1 = new IntArrHolder();
		final int[][] arr1 = new int[bw][bh];
		obj1.arr = arr1;
		obj1.filter = a1;
		list.add(obj1);

		final IntArrHolder obj2 = new IntArrHolder();
		final int[][] arr2 = new int[bw][bh];
		obj2.arr = arr2;
		obj2.filter = a2;
		list.add(obj2);

		final int c = a1.length / 2;

		for (int i = c; i < bi.getWidth() - c; i++) {
			for (int j = c; j < bi.getHeight() - c; j++) {
				int val = bi.getRGB(i, j);
				val = val & 0xff;
				contribToArrInt(val, i, j, list, c);
			}
		}

		return list;
	}

	/**
	 * Adds filter multipled values of pixel onto the output matrix for a
	 * DoubleArrHolder list input
	 * 
	 * @param val
	 * @param i
	 * @param j
	 * @param list
	 * @param c
	 */
	private void contribToArr(int val, int i, int j, List<DoubleArrHolder> list, int c) {

		for (int m = 0; m < list.size(); m++) {
			final double[][] arr = list.get(m).arr;
			final double[][] filter = list.get(m).filter;

			final double[][] filterxVal = multiply2DArrayWithConstant(filter, val);
			mirrorFlipAdd(arr, filterxVal, i, j, c);
		}
	}

	/**
	 * Adds filter multipled values of pixel onto the output matrix for a
	 * IntArrHolder list input
	 * 
	 * @param val
	 * @param i
	 * @param j
	 * @param list
	 * @param c
	 */
	private void contribToArrInt(int val, int i, int j, List<IntArrHolder> list, int c) {

		for (int m = 0; m < list.size(); m++) {
			final int[][] arr = list.get(m).arr;
			final int[][] filter = list.get(m).filter;

			final int[][] filterxVal = multiply2DArrayWithConstant(filter, val);
			mirrorFlipAdd(arr, filterxVal, i, j, c);
		}
	}

	/**
	 * Flips the mirrored array and adds it to output int array.
	 * 
	 * @param arr
	 * @param filterxVal
	 * @param i
	 * @param j
	 * @param c
	 */
	private void mirrorFlipAdd(int[][] arr, int[][] filterxVal, int i, int j, int c) {

		for (int m = 0; m < filterxVal.length; m++) {
			for (int n = 0; n < filterxVal.length; n++) {
				final int val = filterxVal[m][n];
				final int i_index = i + c - m;
				final int j_index = j + c - n;

				arr[i_index][j_index] = arr[i_index][j_index] + val;
			}
		}
	}

	/**
	 * Flips the mirrored array and adds it to output double array.
	 * 
	 * @param arr
	 * @param filterxVal
	 * @param i
	 * @param j
	 * @param c
	 */
	private void mirrorFlipAdd(double[][] arr, double[][] filterxVal, int i, int j, int c) {

		for (int m = 0; m < filterxVal.length; m++) {
			for (int n = 0; n < filterxVal.length; n++) {
				final double val = filterxVal[m][n];
				final int i_index = i + c - m;
				final int j_index = j + c - n;

				arr[i_index][j_index] = arr[i_index][j_index] + val;
			}
		}
	}
	// convolution method end

	// Edge detection methods start
	/**
	 * Used as part of edge detection to convert low threshold values to high
	 * threshold ones if atleast 1 neighbout is a high threshold pixel.
	 * 
	 * @param oi
	 * @param val
	 * @param valArr
	 * @param i
	 * @param j
	 * @param highThres
	 */
	private void compareNndSet(BufferedImage oi, int val, double[][] valArr, int i, int j, double highThres) {
		final double v1 = valArr[i][j];
		final double v2 = valArr[i][j - 1];
		final double v3 = valArr[i][j + 1];
		final double v4 = valArr[i + 1][j];
		final double v5 = valArr[i + 1][j - 1];
		final double v6 = valArr[i + 1][j + 1];
		final double v7 = valArr[i - 1][j];
		final double v8 = valArr[i - 1][j - 1];
		final double v9 = valArr[i - 1][j + 1];

		if (v1 >= highThres || v2 >= highThres || v3 >= highThres || v4 >= highThres || v5 >= highThres
				|| v6 >= highThres || v7 >= highThres || v8 >= highThres || v9 >= highThres) {
			oi.setRGB(i, j, val);
		}
	}

	/**
	 * Generates an image with edges after 1. Computing gradient intensity and
	 * direction. 2. Gets the graident angle and select the most intense pixel only
	 * (non Max supression) 3. Mark high threshold pixels onto image 4. Convert low
	 * threshold pixels to high threshold and mark these with gray lines.
	 * 
	 * @param bw
	 * @param bh
	 * @param sx
	 * @param ex
	 * @param sy
	 * @param ey
	 * @param lt
	 * @param ht
	 * @param valList
	 * @return
	 */
	private BufferedImage genImgForEdgeDetect(int bw, int bh, int sx, int ex, int sy, int ey, double lt, double ht,
			List<IntArrHolder> valList) {

		double maxVal = 0.0;
		double minVal = 1000.0;
		double avgVal = 0.0;

		BufferedImage oi = new BufferedImage(bw, bh, BufferedImage.TYPE_BYTE_GRAY);

		final int[][] anglev = new int[bw][bh];
		final double[][] valArr = new double[bw][bh];

		final int[][] cxarr = valList.get(0).arr;
		final int[][] cyarr = valList.get(1).arr;

		for (int i = sx; i < ex; i++) {
			for (int j = sy; j < ey; j++) {
				final double yd = (double) cyarr[i][j];
				final double xd = (double) cxarr[i][j];

				final int val = (int) Math.sqrt(Math.pow(xd, 2.0) + Math.pow(yd, 2.0));
				if (val > maxVal) {
					maxVal = val;
				}

				avgVal += val;

				if (val < minVal) {
					minVal = val;
				}

				valArr[i][j] = val;

				anglev[i][j] = getStepAngle(yd, xd);
			}
		}

		System.out.println("Max Val: " + maxVal);
		System.out.println("Min Val: " + minVal);
		System.out.println("Avg Val: " + avgVal / ((ex - sx) * (ey - sy)));

		double highThres = maxVal * ht;
		double lowThres = highThres * lt;

		if (ht > 1.0) {
			highThres = ht;
			lowThres = lt;
		}

		for (int i = sx; i < ex; i++) {
			for (int j = sy; j < ey; j++) {
				final int sangle = anglev[i][j];
				final double val = valArr[i][j];

				double a = 0.0;
				double b = 0.0;

				switch (sangle) {
				case 0:
					a = valArr[i][j + 1];
					b = valArr[i][j - 1];
					break;
				case 45:
					a = valArr[i + 1][j - 1];
					b = valArr[i - 1][j + 1];
					break;
				case 90:
					a = valArr[i + 1][j];
					b = valArr[i - 1][j];
					break;
				case 135:
					a = valArr[i - 1][j - 1];
					b = valArr[i + 1][j + 1];
					break;
				}

				if (val >= a && val >= b) {

					if (val >= highThres) {
						oi.setRGB(i, j, 0xffffff);
					} else if (val > lowThres) {
						compareNndSet(oi, 0x7D7D7D, valArr, i, j, highThres);
					}
				}
			}
		}

		return oi;
	}

	/**
	 * Get quantized step angles
	 * 
	 * @param yd
	 * @param xd
	 * @return
	 */
	private int getStepAngle(double yd, double xd) {

		double angle = Math.atan2(yd, xd) * 180.0 / Math.PI;
		int iangle = 0;
		if (angle < 0) {
			angle += 180.0;
		}

		if ((angle < 22.5 && angle >= 0.0) || (angle < 180.0 && angle >= 157.5)) {
			iangle = 0;
		} else if (angle >= 22.5 && angle < 67.5) {
			iangle = 45;
		} else if (angle >= 67.5 && angle < 112.5) {
			iangle = 90;
		} else if (angle >= 112.5 && angle < 157.5) {
			iangle = 135;
		}

		return iangle;
	}
	// Edge detection methods end

	// filter methods - start
	/**
	 * Return gaussian kernel with given size and spread
	 * 
	 * @param size
	 * @param sigma
	 * @return
	 */
	private double[][] gaussianKernel(int size, double sigma) {
		final int nsize = size / 2;
		size = (2 * nsize) + 1;
		final double[][] g = new double[size][size];

		final double sigma2 = Math.pow(sigma, 2.0);
		final double normal = 1 / (2.0 * Math.PI * sigma2);

		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				final double x = -nsize + j;
				final double y = -nsize + i;
				g[i][j] = normal * Math.exp(-((Math.pow(x, 2) + Math.pow(y, 2)) / (2.0 * sigma2)));
			}
		}

		return g;
	}

	/**
	 * Return sobel filter for computing Y gradient
	 * 
	 * @return
	 */
	private int[][] getYFilter() {
		final int[][] arr = new int[3][3];
		arr[0][0] = 1;
		arr[0][1] = 2;
		arr[0][2] = 1;
		arr[1][0] = 0;
		arr[1][1] = 0;
		arr[1][2] = 0;
		arr[2][0] = -1;
		arr[2][1] = -2;
		arr[2][2] = -1;

		return arr;
	}

	/**
	 * Return sobel filter for computing X gradient
	 * 
	 * @return
	 */
	private int[][] getXFilter() {
		final int[][] arr = new int[3][3];
		arr[0][0] = -1;
		arr[0][1] = 0;
		arr[0][2] = 1;
		arr[1][0] = -2;
		arr[1][1] = 0;
		arr[1][2] = 2;
		arr[2][0] = -1;
		arr[2][1] = 0;
		arr[2][2] = 1;

		return arr;
	}
	// filter methods - end
}

/**
 * Internal class for transporting primitive double arrays between methods
 * 
 */
class DoubleArrHolder {

	double[][] arr;
	double[][] filter;
}

/**
 * Internal class for transporting primitive integer arrays between methods
 * 
 */
class IntArrHolder {

	int[][] arr;
	int[][] filter;
}
