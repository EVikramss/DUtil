This utility is for generating edge detected images/notan images on low end devices which support java but don't have support for python numpy and data science libraries.

-> Supports conversion to gray scale, edge detection, notan, gamut map, drawing a scale over the final image & gaussian blur
-> Supports reading jpg, png and other common formats and outputs a jpg file only.
-> Supports combining multiple operations using '::' symbol.

Usage:
1. Make sure java is on the classpath.
2. Run 'javac DUtil.java' to compile to class file
3. Run 'java DUtil ImgFile "cbw::b 1.0 5::e 0.05 0.09::r"' to generate the output image after following below steps
	a. cbw - convert to gray scale
	b. b 1.0 5 - blur image with gaussian kernel of size 5 and standard deviation of 1.0
	c.  e 0.05 0.09 - edge detect with low threshold of 0.05 and high threshold of 0.09
	d. r - draw a scale with spacing of image width - height /10. Spacing can also be passed as arguments
4. Run 'java DUtil ImgFile "n 4"' to generate a notan with 4 values. 
