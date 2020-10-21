package org.apache.hadoop.yarn.examples;

import java.util.Random;

public class JavaPi {
	public static void main(String[] args) {
		System.out.println("start JavaPi....");
		int n=1000;
		cut(n);
	}
	private static double caculateAcreage(double xPosition,double yPosition){
		return xPosition*xPosition+yPosition*yPosition;
	}
	static void cut(int n){
		int countInCircle = 0, i, resulttimes;
		double x, y; 
		Random s = new Random();
		for (i = 1; i <= n; i++) {
			x = s.nextDouble(); 
			y = s.nextDouble(); 
			if (caculateAcreage(x,y)<= 1.0)
			countInCircle++; 
		}
		System.out.println("The result of pai is " + (double) countInCircle / n* 4); 
	}
}
