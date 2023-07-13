package org.openstatic.aprs.parser;

import java.io.Serializable;

public class CourseAndSpeedExtension extends DataExtension implements Serializable 
{
	private static final long serialVersionUID = 1L;
	private int course;
	private int speed;

	public int getCourse() {
		return course;
	}

	public void setCourse(int course) {
		this.course = course;
	}

	public int getSpeed() {
		return speed;
	}

	public void setSpeed(int speed) {
		this.speed = speed;
	}
	
	@Override
	public String toString() {
		return "Moving "+speed+" kts @ "+course+" deg";
	}
	
	@Override
	public String toSAEString() {
		return "Moving "+Utilities.ktsToMph(speed)+" mph @ "+course+" deg";
	}
}
