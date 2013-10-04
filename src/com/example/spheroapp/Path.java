package com.example.spheroapp;

public class Path {
	private double degree;
	private double distance;
	
	public Path(double degree, double distance)
	{
		this.degree = degree;
		this.distance = distance;
	}
	
	public double getDegree()
	{
		return this.degree;
	}
	public double getDistance()
	{
		return this.distance;
	}
	public void setDegree(double degree)
	{
		this.degree = degree;
	}
	public void setDistance(double distance)
	{
		this.distance = distance;
	}
}
