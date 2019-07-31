package com.ncr.passport.hyd.imagesteganographyservice;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ImageSteganographyController {

	@ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "Both Image data and message needs to be specified.") // 400
	@ExceptionHandler(IllegalArgumentException.class)
	public void illegalArgument() {
		// Nothing to do
	}

	@ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "Image could not be processed.") // 400
	@ExceptionHandler(IOException.class)
	public void processingError() {
		// Nothing to do
	}

	@RequestMapping("/")
	public String index() {
		return "Use encode and decode endpoints.";
	}

	@RequestMapping(value = "/encodepng", produces = MediaType.IMAGE_PNG_VALUE, method = RequestMethod.POST)
	public byte[] encode(String message, @RequestParam("image") MultipartFile image) throws IOException {

		if (image == null || image.isEmpty() || message == null || message.isEmpty()) {
			throw new IllegalArgumentException();
		}

		try (InputStream imageInputStream = image.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			BufferedImage bufferedImage = ImageIO.read(imageInputStream);

			// user space is not necessary for Encrypting
			BufferedImage newImage = user_space(bufferedImage);
			newImage = add_text(newImage, message);

			ImageIO.write(newImage, "png", out);
			return out.toByteArray();
		}
	}

	@RequestMapping(value = "/encodetiff", produces = "image/tiff", method = RequestMethod.POST)
	public byte[] encodeTiff(String message, @RequestParam("image") MultipartFile image) throws IOException {

		if (image == null || image.isEmpty() || message == null || message.isEmpty()) {
			throw new IllegalArgumentException();
		}

		try (InputStream imageInputStream = image.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			BufferedImage bufferedImage = ImageIO.read(imageInputStream);

			// user space is not necessary for Encrypting
			BufferedImage newImage = user_space(bufferedImage);
			newImage = add_text(newImage, message);

			ImageIO.write(newImage, "tiff", out);
			return out.toByteArray();
		}
	}

	@RequestMapping(value = "/decodepng", method = RequestMethod.POST)
	public String decode(@RequestParam("image") MultipartFile image) throws IOException {

		if (image == null || image.isEmpty()) {
			throw new IllegalArgumentException();
		}
		
		try (InputStream imageInputStream = image.getInputStream()) {
			BufferedImage bufferedImage = ImageIO.read(imageInputStream);

			// user space is not necessary for Encrypting
			BufferedImage newImage = user_space(bufferedImage);
			byte[] decode = decode_text(get_byte_data(newImage));

			return new String(decode);
		}
	}

	@RequestMapping(value = "/decodetiff", method = RequestMethod.POST)
	public String decodeTiff(@RequestParam("image") MultipartFile image) throws IOException {

		if (image == null || image.isEmpty()) {
			throw new IllegalArgumentException();
		}

		try (InputStream imageInputStream = image.getInputStream()) {
			BufferedImage bufferedImage = ImageIO.read(imageInputStream);

			// user space is not necessary for Encrypting
			BufferedImage newImage = user_space(bufferedImage);
			byte[] decode = decode_text(get_byte_data(newImage));

			return new String(decode);
		}
	}

	/*
	 * Handles the addition of text into an image
	 * @param image The image to add hidden text to
	 * @param text The text to hide in the image
	 * @return Returns the image with the text embedded in it
	 */
	private BufferedImage add_text(BufferedImage image, String text) {
		// convert all items to byte arrays: image, message, message length
		byte img[] = get_byte_data(image);
		byte msg[] = text.getBytes();
		byte len[] = bit_conversion(msg.length);
		try {
			encode_text(img, len, 0); // 0 first positiong
			encode_text(img, msg, 32); // 4 bytes of space for length: 4bytes*8bit = 32 bits
		} catch (Exception e) {
			e.printStackTrace();
		}
		return image;
	}

	/*
	 * Creates a user space version of a Buffered Image, for editing and saving bytes
	 * @param image The image to put into user space, removes compression interferences
	 * @return The user space version of the supplied image
	 */
	private BufferedImage user_space(BufferedImage image) {
		// create new_img with the attributes of image
		BufferedImage new_img = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
		Graphics2D graphics = new_img.createGraphics();
		graphics.drawRenderedImage(image, null);
		graphics.dispose(); // release all allocated memory for this image
		return new_img;
	}

	/*
	 * Gets the byte array of an image
	 * @param image The image to get byte data from
	 * @return Returns the byte array of the image supplied
	 * @see Raster
	 * @see WritableRaster
	 * @see DataBufferByte
	 */
	private byte[] get_byte_data(BufferedImage image) {
		WritableRaster raster = image.getRaster();
		DataBufferByte buffer = (DataBufferByte) raster.getDataBuffer();
		return buffer.getData();
	}

	/*
	 * Gernerates proper byte format of an integer
	 * @param i The integer to convert
	 * @return Returns a byte[4] array converting the supplied integer into bytes
	 */
	private byte[] bit_conversion(int i) {
		// originally integers (ints) cast into bytes
		// byte byte7 = (byte)((i & 0xFF00000000000000L) >>> 56);
		// byte byte6 = (byte)((i & 0x00FF000000000000L) >>> 48);
		// byte byte5 = (byte)((i & 0x0000FF0000000000L) >>> 40);
		// byte byte4 = (byte)((i & 0x000000FF00000000L) >>> 32);
		// only using 4 bytes
		byte byte3 = (byte) ((i & 0xFF000000) >>> 24); // 0
		byte byte2 = (byte) ((i & 0x00FF0000) >>> 16); // 0
		byte byte1 = (byte) ((i & 0x0000FF00) >>> 8); // 0
		byte byte0 = (byte) ((i & 0x000000FF));
		// {0,0,0,byte0} is equivalent, since all shifts >=8 will be 0
		return (new byte[] {byte3, byte2, byte1, byte0});
	}

	/*
	 * Encode an array of bytes into another array of bytes at a supplied offset
	 * @param image Array of data representing an image
	 * @param addition Array of data to add to the supplied image data array
	 * @param offset The offset into the image array to add the addition data
	 * @return Returns data Array of merged image and addition data
	 */
	private byte[] encode_text(byte[] image, byte[] addition, int offset) {
		// check that the data + offset will fit in the image
		if (addition.length + offset > image.length) {
			throw new IllegalArgumentException("File not long enough!");
		}
		// loop through each addition byte
		for (int i = 0; i < addition.length; ++i) {
			// loop through the 8 bits of each byte
			int add = addition[i];
			for (int bit = 7; bit >= 0; --bit, ++offset) // ensure the new offset value carries on through both loops
			{
				// assign an integer to b, shifted by bit spaces AND 1
				// a single bit of the current byte
				int b = (add >>> bit) & 1;
				// assign the bit by taking: [(previous byte value) AND 0xfe] OR bit to add
				// changes the last bit of the byte in the image to be the bit of addition
				image[offset] = (byte) ((image[offset] & 0xFE) | b);
			}
		}
		return image;
	}

	/*
	 * Retrieves hidden text from an image
	 * @param image Array of data, representing an image
	 * @return Array of data which contains the hidden text
	 */
	private byte[] decode_text(byte[] image) {
		int length = 0;
		int offset = 32;
		// loop through 32 bytes of data to determine text length
		for (int i = 0; i < 32; ++i) // i=24 will also work, as only the 4th byte contains real data
		{
			length = (length << 1) | (image[i] & 1);
		}

		byte[] result = new byte[length];

		// loop through each byte of text
		for (int b = 0; b < result.length; ++b) {
			// loop through each bit within a byte of text
			for (int i = 0; i < 8; ++i, ++offset) {
				// assign bit: [(new byte value) << 1] OR [(text byte) AND 1]
				result[b] = (byte) ((result[b] << 1) | (image[offset] & 1));
			}
		}
		return result;
	}

}
