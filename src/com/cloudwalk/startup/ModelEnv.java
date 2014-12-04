/*
 * @(#)ModelEnv.java (part of 'Flight Club')
 * 
 * This code is covered by the GNU General Public License
 * detailed at http://www.gnu.org/copyleft/gpl.html
 *	
 * Flight Club docs located at http://www.danb.dircon.co.uk/hg/hg.htm
 * Copyright 2001-2003 Dan Burton <danb@dircon.co.uk>
 */
package com.cloudwalk.startup;

import java.io.*;
import java.awt.*;

import android.content.Context;
import android.content.SharedPreferences;

/**
   This interface enables a ModelViewer to be used in either an
   applet or an application (a frame).  
*/
public interface ModelEnv {
    //Image createImage(int w, int h);
    InputStream openFile(String s);
  //  InputStream openFile(String s);
    String getTask(); // hack - should extend interface ?
    int getPilotType();
    void setPilotType(int pilotType);
    void setTask(String task);
    String getHostPort(); 
    int[] getTypeNums();
	void play(float sound, int index, int loop);
	Context getContext();
	SharedPreferences getPrefs();
}
   
