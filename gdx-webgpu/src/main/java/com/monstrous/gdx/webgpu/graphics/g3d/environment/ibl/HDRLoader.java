
// Based on gdx-gltf by MGSX-dev (IBLComposer class)
// https://github.com/mgsx-dev/gdx-gltf/tree/master

package com.monstrous.gdx.webgpu.graphics.g3d.environment.ibl;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.BufferUtils;
import com.github.xpenatan.webgpu.WGPUTextureFormat;
import com.monstrous.gdx.webgpu.graphics.WgTexture;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/** Usage:
 * HDRLoader loader = new HDRLoader();
 * loader.loadHDR(Gdx.files.internal("filename.hdr"));
 * WgTexture tex = loader.getHDRTexture(true);
 *
 * todo rethink this interface? E.g. as one static method?
 */

public class HDRLoader {

	public RGBE.Header hdrHeader;
	private byte[] hdrData;
	private WgTexture textureRaw;


	public void loadHDR(FileHandle file) throws IOException{
		DataInputStream in = null;
		try{
			in = new DataInputStream(new BufferedInputStream(file.read()));
			hdrHeader = RGBE.readHeader(in);
			hdrData = new byte[hdrHeader.getWidth() * hdrHeader.getHeight() * 4];
			RGBE.readPixelsRawRLE(in, hdrData, 0, hdrHeader.getWidth(), hdrHeader.getHeight());
		}finally{
			if(in != null) in.close();
		}
	}

	public WgTexture getHDRTexture(boolean asLDR) {
		if(textureRaw == null){
			// convert to pixmap applying optional exposure
	        float [] pixel = new float[3];
	        int imageWidth = hdrHeader.getWidth();
	        int imageHeight = hdrHeader.getHeight();
			int numComponents = 4;

	        if(asLDR){

                ByteBuffer pixels = BufferUtils.newUnsafeByteBuffer(imageHeight*imageWidth*4);
				int index = 0;

	        	for(int y=0 ; y<imageHeight ; y++){
	        		for(int x=0 ; x<imageWidth ; x++){
	        			int idx = (y*imageWidth+x)*4;
	        			RGBE.rgbe2float(pixel, hdrData, idx); // TODO exposure should be done in this call for best precision.

						for(int i=0 ; i<3 ; i++){
	        				pixel[i] = (float)Math.pow(pixel[i], 0.45f);
                            pixel[i] = Math.min(pixel[i], 1.0f);			// clamp to be LDR
	        			}

                        pixels.put((byte)(pixel[0] * 255));
                        pixels.put((byte)(pixel[1] * 255));
                        pixels.put((byte)(pixel[2] * 255));
                        pixels.put((byte)255);
	        		}
	        	}
                textureRaw = new WgTexture("", imageWidth, imageHeight,false, false, WGPUTextureFormat.RGBA8Unorm,1);
                pixels.flip();
                textureRaw.load(pixels);
                BufferUtils.disposeUnsafeByteBuffer(pixels);
	        }
	        else{
                throw new RuntimeException("getHDRTexture: only LDR supported for now");
//				short[] pixels = new short[imageWidth * imageHeight * numComponents];	// 16 bit "short" to store 16-bit floats
//				int index = 0;
//				for(int y=0 ; y<imageHeight ; y++){
//					for(int x=0 ; x<imageWidth ; x++){
//						int idx = (y*imageWidth+x)*4;
//						RGBE.rgbe2float(pixel, hdrData, idx); // TODO exposure should be done in this call for best precision.
////						for(int i=0 ; i<3 ; i++){
////	        				pixel[i] = (float)Math.pow(pixel[i], 0.5f);
////	        			}
//
//						pixels[index++] = convert(pixel[0]);
//						pixels[index++] = convert(pixel[1]);
//						pixels[index++] = convert(pixel[2]);
//						pixels[index++] = convert(1.0f);
//					}
//				}
//				textureRaw = new WgTexture(imageWidth, imageHeight, false, false, WGPUTextureFormat.RGBA16Float, 1);
//				textureRaw.fill(pixels);

	        }
		}
		return textureRaw;
	}

	// map a float to a short corresponding to a half-precision float (16 bits)
	// Java can't work with this, but the GPU can.
	private short convert( float value ){
		int intValue = fromFloat(value);
		return (short)intValue;
	}

	// returns all higher 16 bits as 0 for all results
	public static int fromFloat( float fval )
	{
		int fbits = Float.floatToIntBits( fval );
		int sign = fbits >>> 16 & 0x8000;          // sign only
		int val = ( fbits & 0x7fffffff ) + 0x1000; // rounded value

		if( val >= 0x47800000 )               // might be or become NaN/Inf
		{                                     // avoid Inf due to rounding
			if( ( fbits & 0x7fffffff ) >= 0x47800000 )
			{                                 // is or must become NaN/Inf
				if( val < 0x7f800000 )        // was value but too large
					return sign | 0x7c00;     // make it +/-Inf
				return sign | 0x7c00 |        // remains +/-Inf or NaN
						( fbits & 0x007fffff ) >>> 13; // keep NaN (and Inf) bits
			}
			return sign | 0x7bff;             // unrounded not quite Inf
		}
		if( val >= 0x38800000 )               // remains normalized value
			return sign | val - 0x38000000 >>> 13; // exp - 127 + 15
		if( val < 0x33000000 )                // too small for subnormal
			return sign;                      // becomes +/-0
		val = ( fbits & 0x7fffffff ) >>> 23;  // tmp exp for subnormal calc
		return sign | ( ( fbits & 0x7fffff | 0x800000 ) // add subnormal bit
				+ ( 0x800000 >>> val - 102 )     // round depending on cut off
				>>> 126 - val );   // div by 2^(1-(exp-127+15)) and >> 13 | exp=0
	}

}
