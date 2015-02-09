/*
  @(#)Obj3dManager.java (part of 'Flight Club')
	
  This code is covered by the GNU General Public License
  detailed at http://www.gnu.org/copyleft/gpl.html
	
  Flight Club docs located at http://www.danb.dircon.co.uk/hg/hg.htm
  Copyright 2001-2002 Dan Burton <danb@dircon.co.uk>
 */
package com.cloudwalk.framework3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * This class manages a list of 3d objects - the model.
 */
public class Obj3dManager {
	protected ModelViewer modelViewer;
	private List<Obj3d> list;

	Obj3dManager(ModelViewer modelViewer) {
		this.modelViewer = modelViewer;
		list = new ArrayList<Obj3d>();
	}

	public void addObj(Obj3d o) {
		synchronized (this) {
			list.add(o);
		}
	}

	public void removeObj(Obj3d o) {
		synchronized (this) {
			list.remove(o);
		}
	}

	public void removeAll() {
		synchronized (this) {
			list.clear();
		}
	}

	public Obj3d obj(int i) {
		return (Obj3d) list.get(i);
	}

	public int size() {
		return list.size();
	}

	/**
	 * Sorts so that the furthest away obj is first in the list. We use the
	 * depth bounding box x_min -> x_max and take its mid point to be THE depth
	 * of the object. This is ok for local objects. ***TODO*** advanced zorder
	 * to handle terrain, roads etc. will be implemented in subclass.
	 */
	public void sortObjects() {
		try {

			if (list.size() >= 2) {
				synchronized (this) {
					Collections.sort(list, new Comparator<Obj3d>() {
						@Override
						public int compare(Obj3d o1, Obj3d o2) {
							// TODO Auto-generated method stub
							return (int) ((o1.getDepthMin() + o1.getDepthMax()) * 100 - (o2.getDepthMin() + o2.getDepthMax()) * 100);
						}
					});
				}
			}
		} catch (Exception e) {
			// TODO: handle exception
		}
	}
}
