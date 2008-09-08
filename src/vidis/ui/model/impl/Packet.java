package vidis.ui.model.impl;

import javax.media.opengl.GL;
import javax.vecmath.Tuple3d;
import javax.vecmath.Vector3d;

import vidis.data.var.AVariable;
import vidis.data.var.IVariableContainer;
import vidis.ui.events.IVidisEvent;
import vidis.ui.model.structure.ASimObject;


public class Packet extends ASimObject {

	public Packet( IVariableContainer c, Link link ) {
		super(c);
		link.addPacket( this );
	}

	private static int displayListId = -1;
	
	@Override
	public void renderObject( GL gl ) {
		if ( displayListId == -1 ) {
			displayListId = gl.glGenLists( 1 );
			preRenderObject( gl );
		}
		gl.glColor3d( 1, 0, 1 );
		gl.glCallList( displayListId );
	}
	
	public void preRenderObject( GL gl ) {
		gl.glNewList( displayListId, GL.GL_COMPILE );
			glut.glutSolidCube( 0.2f );
		gl.glEndList();
	}

	@Override
	protected void handleEvent( IVidisEvent e ) {
		
	}

	public Vector3d getPosition() {
		try {
			Tuple3d pos = (Tuple3d) getVariableById( AVariable.COMMON_IDENTIFIERS.POSITION ).getData();
			return new Vector3d( pos );
		}
		catch ( Exception e ) {
			return null;
		}
	}


}
