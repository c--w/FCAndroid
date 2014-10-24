package com.cloudwalk.client;
/*
 * Aggregate class to return from Node.search
 */
public class LiftSourceGlide {
	LiftSource ls;
	int glideIndex;
	public LiftSourceGlide(LiftSource ls, int glideIndex) {
		this.ls = ls;
		this.glideIndex = glideIndex;
	}

}
