package com.cellmate.headtrack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLSurfaceView;

import android.opengl.GLU;
import android.opengl.GLUtils;

public class OpenGLRenderer implements GLSurfaceView.Renderer {
	final static float targetVertices[] = new float[] {
		-1.0f, 1.0f,.0f,  // left up
		1.0f, 1.0f,.0f,  // right up
		-1.0f,-1.0f,.0f, // left bottom
		1.0f,-1.0f,.0f	   // right bottom
	};
	final static float targetVerticesTexCoord[] = new float[] {
		0.0f,0.0f,
		1.0f,0.0f,
		0.0f,1.0f,
		1.0f,1.0f			 
	};
	
	float linesVertices[] = new float[] {
			0.0f, 0.0f, 0.0f,
			0.0f, 0.0f, 0.0f	 
	};
	
	float gridVertices[];
	float backgroundVertices[];
	float backgroundTexVertices[];
	TexFont fnt;
	
	/////////////////////////////////////////////////////
    // Static variables
	/////////////////////////////////////////////////////
    static float screenHeightinMM = 6 * 25.4f; // screen height is 6 inches, 152,6
    static int m_dwWidth = 480;
    static int m_dwHeight = 800;

    //headposition
    static float headX = 0;
    static float headY = 0;
    static float headDist = 2;
    static float cameraVerticaleAngle = 0; //begins assuming the camera is point straight forward
    static boolean cameraIsAboveScreen = true;//has no affect until zeroing and then is set automatically.
	/////////////////////////////////////////////////////
    // Numerical settings 
    /////////////////////////////////////////////////////
    int numGridlines = 10;
    float boxdepth = 8;
    float fogdepth = 5;
    int numTargets = 10;
    int numInFront = 3;
    float targetScale = .065f;
    float screenAspect = 0;
    int lineDepth = -200;
    int backgroundStepCount = 10;
    /////////////////////////////////////////////////////
    boolean showGrid = true;//true
    boolean showHelp = false; // Press H to hide
    boolean showTargets = true;//true;
    boolean showLines = true;//true;
    boolean showBackground = false;//false
    /////////////////////////////////////////////////////
    Vector3[] targetPositions;
    Vector3[] targetSizes;

    int textLineDepth = 10;
    
    static int[] textureIDs = new int[3];  // 0 - target texture, 1 - background texture
    
    FloatBuffer targetsBuffer, targetsTexBuffer, linesBuffer, gridBuffer, backgroundBuffer, backgroundTexBuffer;

    static public boolean isRendering = false;
	static GL10 gl10;

	public float FaceX = 100.0f;
	public float FaceY = 100.0f;
	public float FaceZ = 300.0f;

	public void handlePressLeft()
	{
		headX -= 0.007f;
	}
	public void handlePressRight()
	{
		 headX += 0.007f;
	}
	public void handlePressUp()
	{
		 headY -= 0.007f;
	}
	public void handlePressDown()
	{
		 headY += 0.007f;
	}
	public void handlePressA()
	{
		headDist -= 0.03f;
	}
	public void handlePressZ()
	{
		headDist += 0.03f;
	}

	
	FloatBuffer makeFloatBuffer(float[] arr) {
		ByteBuffer bb = ByteBuffer.allocateDirect(arr.length*4);
		bb.order(ByteOrder.nativeOrder());
		FloatBuffer fb = bb.asFloatBuffer();
		fb.put(arr);
		fb.position(0);
		return fb;
	}
	
	Context context;   // Application's context
	   // Constructor with global application context
	public OpenGLRenderer(Context context) {
	   this.context = context;
	}

	protected void init(GL10 gl) {	
	    if (screenAspect == 0)//only override if it's emtpy
	        screenAspect = m_dwWidth / (float)m_dwHeight;
	    		
	    gl10 = gl;
	    
	     // Create font
	     fnt = new TexFont(context,gl);
	     
	     // Load font file from Assets
	     try
	     {
	      // Note, this is an 8bit BFF file and will be used as alpha channel only
	      //fnt.LoadFont("Arial-alpha.bff",gl);
	      fnt.LoadFont("Arial.bff",gl);
         }
	     catch (IOException e) 
	     {
	      e.printStackTrace();
	     }

	    
		//Load the buffer and stuff		
		// background is black
		gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
		gl.glClearDepthf(1.0f);

		gl.glShadeModel(GL10.GL_SMOOTH);
		
		// GL.PolygonMode(MaterialFace.FrontAndBack, PolygonMode.Fill);
		//gl.glCullFace(GL10.GL_FRONT_AND_BACK);
		
		gl.glEnable(GL10.GL_COLOR_MATERIAL);
		gl.glMaterialf(GL10.GL_FRONT_AND_BACK,GL10.GL_COLOR_MATERIAL,(float)GL10.GL_AMBIENT_AND_DIFFUSE);		
		
		gl.glEnable(GL10.GL_DEPTH_TEST );
		
	    // creates array for grid used later in drawing
	    CreateGridGeometry();
	    CreateTargetGeometry();
	    CreateBackgroundGeometry();

	    // Randomizes locations of targets and lengths (depth)
	    InitTargets();
	
	    SetupMatrices(gl);
		
	 // Load Textures
	    loadTexture(gl, context, 0);    // Load image into Texture (NEW)
	    loadTexture(gl, context, 1);
	    
	}
	
    // Load an image into GL texture
    public void loadTexture(GL10 gl, Context context, int index) {
       GL11 gl11 = (GL11)gl;

      gl11.glGenTextures(1, textureIDs, index); // Generate texture-ID array

      gl11.glBindTexture(GL11.GL_TEXTURE_2D, textureIDs[index]);   // Bind to texture ID
      // Set up texture filters

      gl11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_GENERATE_MIPMAP, GL11.GL_TRUE);
      gl11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
      gl11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

      // Construct an input stream to texture image "res\drawable\nehe.png"
      // Choose different image accordinmg to loaded texture
      InputStream istream = null;

      if (index == 0)
      {
          istream = context.getResources().openRawResource(+ R.drawable.target);
      }
      else if (index == 1)
      {
          istream = context.getResources().openRawResource(+ R.drawable.stad_2);
      }

      Bitmap bitmap;
      try {
         // Read and decode input as bitmap
         bitmap = BitmapFactory.decodeStream(istream);
      } finally {
         try {
            istream.close();
         } catch(IOException e) { }
      }

      // Build Texture from loaded bitmap for the currently-bind texture ID
      GLUtils.texImage2D(GL11.GL_TEXTURE_2D, 0, bitmap, 0);
      bitmap.recycle();
    }

    private void SetupMatrices(GL10 gl)
    {
    	gl.glViewport(0,0,m_dwWidth,m_dwHeight);
    	
        // Set up our view matrix. A view matrix can be defined given an eye point,
        // a point to lookat, and a direction for which way is up. Here, we set the
        // eye five units back along the z-axis and up three units, look at the
        // origin, and define "up" to be in the y-direction.
		// device.Transform.View = Matrix.LookAtLH(new Vector3(mouseCursor.X, mouseCursor.Y, -5.0f), new Vector3(0.0f, 0.0f, 0.0f), new Vector3(0.0f, 1.0f, 0.0f));
		gl.glMatrixMode(GL10.GL_MODELVIEW);
		gl.glLoadIdentity();
		
		GLU.gluLookAt(gl, headX, headY, headDist, headX, headY, 0, 0.0f, 1.0f, 0.0f);
        // For the projection matrix, we set up a perspective transform (which
        // transforms geometry from 3D view space to 2D viewport space, with
        // a perspective divide making objects smaller in the distance). To build
        // a perpsective transform, we need the field of view (1/4 pi is common),
        // the aspect ratio, and the near and far clipping planes (which define at
        // what distances geometry should be no longer be rendered).

        //compute the near plane so that the camera stays fixed to -.5f*screenAspect, .5f*screenAspect, -.5f,.5f
        //compting a closer plane rather than simply specifying xmin,xmax,ymin,ymax allows things to float in front of the display
        float nearPlane = 0.05f;
        float farPlane = 500f; // 100f ?
		
		gl.glMatrixMode(GL10.GL_PROJECTION);
		gl.glLoadIdentity();

		gl.glFrustumf(nearPlane * ( -.5f * screenAspect - headX) / headDist,
					                nearPlane * (.5f * screenAspect - headX) / headDist,
					                nearPlane * (-.5f - headY) / headDist,
					                nearPlane * (.5f - headY) / headDist,
					                nearPlane, farPlane);
    }
    
    
    @Override
	public void onDrawFrame(GL10 gl) {
		isRendering = true;
			 
		gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);

            // Setup the world, view, and projection matrices
		headX = 0.10f - (FaceX/(10 * screenHeightinMM * 2.5f));
		headY = 0.10f - (FaceY/(10 * screenHeightinMM * 2.5f));
		headDist = 3.7f - (FaceZ/(screenHeightinMM * 3.0f));
        SetupMatrices(gl);

    	gl.glMatrixMode(GL10.GL_MODELVIEW);

        EnableFog(gl);


        if (showGrid)
            {
            	gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
            	
            	gl.glDisable(GL10.GL_TEXTURE_2D);
            	gl.glDisable(GL10.GL_BLEND);
            	gl.glDisable(GL10.GL_ALPHA_TEST );
            
            	gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);            	
            	gl.glVertexPointer(3, GL10.GL_FLOAT, 0, gridBuffer);

                // translations to the World view in DirectX are done on the GL
                // zoom out by 1/2 boxdepth, move 0.5 right and 0.5 above the floor
            	gl.glPushMatrix(); // copies the current matrix and add the copy to the top of the stack
            	gl.glScalef(screenAspect, 1, 1);
                gl.glTranslatef(-.5f, -.5f, -1 * boxdepth / 2);
                gl.glDrawArrays(GL10.GL_LINES, 0, gridVertices.length / 3);
                gl.glPopMatrix();

                gl.glPushMatrix();              
                gl.glTranslatef(.5f * screenAspect, -.5f, 0);                  
                gl.glRotatef(90f,0, 1, 0);               
                gl.glScalef(1 * boxdepth / 2, 1, 1);
                gl.glDrawArrays(GL10.GL_LINES, 0, gridVertices.length / 3);
                gl.glPopMatrix();

                // Left
                gl.glPushMatrix();
                gl.glTranslatef(-.5f * screenAspect, -.5f, 0);
                gl.glRotatef(90f,0, 1, 0);
                gl.glScalef(1 * boxdepth / 2, 1, 1);
                gl.glDrawArrays(GL10.GL_LINES, 0, gridVertices.length / 3);
                gl.glPopMatrix();

                // ceiling
                gl.glPushMatrix();
                gl.glTranslatef(-.5f * screenAspect, .5f, -1f * boxdepth / 2);  
                gl.glRotatef(90f,1, 0, 0);
                gl.glScalef(screenAspect, 1 * boxdepth / 2, 1);
                gl.glDrawArrays(GL10.GL_LINES, 0, gridVertices.length / 3);
                gl.glPopMatrix();

                // floor
                gl.glPushMatrix();
                gl.glTranslatef(-.5f * screenAspect, -.5f, -1f * boxdepth / 2);
                gl.glRotatef(90f,1, 0, 0);
                gl.glScalef(screenAspect, 1 * boxdepth / 2, 1);
                gl.glDrawArrays(GL10.GL_LINES, 0, gridVertices.length / 3);
                gl.glPopMatrix();        
                
             // Disable the vertices buffer.
                gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);
            }
            
            if (showLines)
            {
            	gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
            	
            	gl.glDisable(GL10.GL_TEXTURE_2D);
            	gl.glDisable(GL10.GL_BLEND);
            	gl.glDisable(GL10.GL_ALPHA_TEST );

            	gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);          
            	gl.glVertexPointer(3, GL10.GL_FLOAT, 0, linesBuffer);	
            	
                for (int i = 0; i < numTargets; i++)
                {
                    gl.glPushMatrix(); // copies the current matrix and add the copy to the top of the stack
                    gl.glTranslatef(targetPositions[i].x, targetPositions[i].y, targetPositions[i].z);
                    gl.glScalef(targetSizes[i].x, targetSizes[i].y, targetSizes[i].z );

                    gl.glDrawArrays(GL10.GL_LINES, 0, linesVertices.length / 3);

                    gl.glPopMatrix();
                }
                
                gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);	
            }

            if (showTargets)//draw targets
            {
            	gl.glEnable(GL10.GL_TEXTURE_2D);
            	
            	////////////////// VBO ///////////////////////////////
            	gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
            	gl.glVertexPointer(3, GL10.GL_FLOAT, 0, targetsBuffer);	
            	
	            gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);	                                  
	            gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, targetsTexBuffer);
	            
	            
	            /////////////////////////////////////////////////////
	            gl.glBindTexture(GL10.GL_TEXTURE_2D, textureIDs[0]);
                gl.glEnable(GL10.GL_COLOR_MATERIAL);

                //Render States   

                ///////////////////////////////////////////////////////////////////////////////
                // ENABLE_TRANSPARENCY (mask + clip)
                ///////////////////////////////////////////////////////////////////////////////                
                gl.glEnable(GL10.GL_BLEND);
                gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);

                gl.glEnable(GL10.GL_ALPHA_TEST);
                gl.glAlphaFunc(GL10.GL_GREATER, 0.5f);                 
                ///////////////////////////////////////////////////////////////////////////////

                ///////////////////////////////////////////////////////////////////////////////
                // I think this part is unnecessary as these are the default values
                ///////////////////////////////////////////////////////////////////////////////
                //Color blending ops (these are the default values as it is)
                float[] color4 = {1f, 1f, 1f, 0};
                
                gl.glTexEnvf(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_MODE, (float)GL10.GL_MODULATE);
                gl.glTexEnvfv(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_COLOR, color4,0);

                //set the first alpha stage to texture alpha
                //gl.glTexEnvf(GL10.GL_TEXTURE_ENV, TextureEnvParameter.Operand0Alpha, (float)TextureEnvParameter.Src1Alpha);
                ///////////////////////////////////////////////////////////////////////////////
                for (int i = 0; i < numTargets; i++)
                {
                	gl.glPushMatrix(); // copies the current matrix and add the copy to the top of the stack
                	gl.glTranslatef(targetPositions[i].x, targetPositions[i].y, targetPositions[i].z);
                	gl.glScalef(targetSizes[i].x, targetSizes[i].y, targetSizes[i].z );

                	gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, targetVertices.length / 3);
                    
                    gl.glPopMatrix();                    
                }

                gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
            	gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);		            
	            
                gl.glDisable(GL10.GL_TEXTURE_2D);
                gl.glDisable(GL10.GL_COLOR_MATERIAL);
            }

            if (showBackground)
            {
                gl.glPushMatrix();
                //gl.glScalef(3, 2, 3);
                gl.glScalef(1.1f, 1.1f, 1.1f);

                gl.glEnable(GL10.GL_TEXTURE_2D);
                gl.glBindTexture(GL10.GL_TEXTURE_2D, textureIDs[1]);

            	gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
            	gl.glVertexPointer(3, GL10.GL_FLOAT, 0, backgroundBuffer);	
            	
	            gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);	                                  
	            gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, backgroundTexBuffer);
	            
                gl.glDisable(GL10.GL_FOG);

                //Render States
                gl.glDisable(GL10.GL_BLEND);
            	gl.glDisable(GL10.GL_ALPHA_TEST );

                float[] color4 = {1f, 1f, 1f, 0};
                
                gl.glTexEnvf(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_MODE, (float)GL10.GL_MODULATE);
                gl.glTexEnvfv(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_COLOR, color4,0);

                gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, backgroundVertices.length / 3);

                gl.glPopMatrix();

                gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
            	gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);	
            	
                gl.glDisable(GL10.GL_TEXTURE_2D);
            }

            if (showHelp)
                RenderText(gl);
            
            isRendering = false;
	}

    void RenderText(GL10 gl)
    {
        gl.glDisable(GL10.GL_DEPTH_TEST);
        gl.glEnable(GL10.GL_TEXTURE_2D);

        textLineDepth = 750;
        fnt.SetScale(1);
        fnt.SetCursor(2, textLineDepth);
        fnt.SetPolyColor(1.0f, 1.0f, 1.0f);
        
        // Output statistics
        DrawTextLine(gl, "Stats---------------");

        DrawTextLine(gl, "Est Head X-Y (mm): " + headX * screenHeightinMM + ", " + headY * screenHeightinMM);
        DrawTextLine(gl, "Est Head Dist (mm): " + headDist * screenHeightinMM);
        DrawTextLine(gl, "Camera Vert Angle (rad): " + cameraVerticaleAngle);
    }
    
    private void DrawTextLine(GL10 gl, String text)
    {
        fnt.Print(gl, text);
        textLineDepth -= 20;
        fnt.SetCursor(2,textLineDepth);
    }
    
    void EnableFog(GL10 gl)
    {
    	gl.glEnable(GL10.GL_FOG);
        float[] color = { 0.0f, 0.0f, 0.0f, 1.0f };
        
        gl.glFogf(GL10.GL_FOG_MODE, GL10.GL_LINEAR);
        gl.glFogfv(GL10.GL_FOG_COLOR,color,0);
        gl.glFogf(GL10.GL_FOG_DENSITY, 0.35f);        
        gl.glHint(GL10.GL_FOG_HINT, GL10.GL_NICEST);
        gl.glFogf(GL10.GL_FOG_START, headDist);
        gl.glFogf(GL10.GL_FOG_END, headDist + fogdepth);
    }
    

	public void CreateBackgroundGeometry()
    {		 
		 backgroundVertices = new float[3*(backgroundStepCount+1)]; 		
		 backgroundTexVertices = new float[2*(backgroundStepCount+1)];
		
       float angleStep = (float)(Math.PI / backgroundStepCount);
       
       int bgIndex = 0, bgTexIndex = 0;
       
       for (int i = 0; i <= backgroundStepCount; i++)
       {
           // On even steps (0, 2, 4)
           if (i % 2 == 0)
           {
           	backgroundVertices[bgIndex] = (float)(java.lang.Math.cos(angleStep * i));
           	backgroundVertices[bgIndex+1] = -1f;
           	backgroundVertices[bgIndex+2] = -(float)(java.lang.Math.sin(angleStep * i));
           	
           	bgIndex += 3;
           	
           	backgroundTexVertices[bgTexIndex] = i / (float)backgroundStepCount;
           	backgroundTexVertices[bgTexIndex+1] = 1f;
           	
           	bgTexIndex += 2;

           }
           else
           {
               // On odd steps (1,3,5)
           	backgroundVertices[bgIndex] = (float)(java.lang.Math.cos(angleStep * i));
           	backgroundVertices[bgIndex+1] = 1f;
           	backgroundVertices[bgIndex+2] = -(float)(java.lang.Math.sin(angleStep * i));
           	
           	bgIndex += 3;
           	
           	backgroundTexVertices[bgTexIndex] = i / (float)backgroundStepCount;
           	backgroundTexVertices[bgTexIndex+1] = 0;
           	
           	bgTexIndex += 2;
           }
       }
       
		 backgroundBuffer = makeFloatBuffer(backgroundVertices);
		 backgroundTexBuffer = makeFloatBuffer(backgroundTexVertices);	

    }


    public void CreateTargetGeometry()
    {
		targetsBuffer = makeFloatBuffer(targetVertices);
		targetsTexBuffer = makeFloatBuffer(targetVerticesTexCoord);	
		
		linesVertices[5] = lineDepth;		
        linesBuffer = makeFloatBuffer(linesVertices);
    }

    // Builds a grid wall which we can later scale, rotate and transform when rendering
    // (I could have used display lists as in the 
    void CreateGridGeometry()
    {
   	 gridVertices = new float[(numGridlines * 13)+2];
   	 
   	 int step = m_dwWidth / numGridlines;

   	 int index = 0;
   	 
        for (int i = 0; i <= numGridlines * 2; i += 2)
        {
       	 gridVertices[index] = (i * step / 2.0f) / m_dwWidth;
       	 gridVertices[index+1] = 0.0f;
       	 gridVertices[index+2] = 0.0f;        	 
       	 gridVertices[index+3] = (i * step / 2.0f) / m_dwWidth;
       	 gridVertices[index+4] = 1.0f;
       	 gridVertices[index+5] = 0.0f;
       	 
       	 index += 6;
        }

        for (int i = 0; i <= numGridlines * 2; i += 2)
        {
       	 gridVertices[index] = 0.0f;
       	 gridVertices[index+1] = (i * step / 2.0f) / m_dwWidth;
       	 gridVertices[index+2] = 0.0f;        	 
       	 gridVertices[index+3] = 1.0f;
       	 gridVertices[index+4] = (i * step / 2.0f) / m_dwWidth;
       	 gridVertices[index+5] = 0.0f;
       	 
       	 index += 6;
        }
        
   	 gridBuffer = makeFloatBuffer(gridVertices);				
    }
    
    
    // Generates random target areas
    public void InitTargets()
    {    			 
        if (targetPositions == null)
            targetPositions = new Vector3[numTargets];
        if (targetSizes == null)
            targetSizes = new Vector3[numTargets];
        float depthStep = (boxdepth / 2.0f) / numTargets;
        float startDepth = numInFront * depthStep;
        
        final Random myRandom = new Random();
        
        for (int i = 0; i < numTargets; i++)
        {
            targetPositions[i] = new Vector3(.7f * screenAspect * (myRandom.nextInt(1000) / 1000.0f - .5f),
                                                .7f * (myRandom.nextInt(1000) / 1000.0f - .5f),
                                                startDepth - i * depthStep);
            if (i < numInFront)//pull in the ones out in front of the display closer the center so they stay in frame
            {
                targetPositions[i].x *= .5f;
                targetPositions[i].y *= .5f;
            }
            targetSizes[i] = new Vector3(targetScale, targetScale, targetScale);
        }
    }


	@Override
	public void onSurfaceChanged(GL10 gl, int width, int height) {
		 m_dwWidth = width;
         m_dwHeight = height;

         screenAspect = m_dwWidth / (float)m_dwHeight;
         SetupMatrices(gl);
	}

	@Override
	public void onSurfaceCreated(GL10 gl, EGLConfig config) {
		init(gl);
	}	
}
