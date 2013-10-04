package com.example.spheroapp;

import java.util.ArrayList;
import java.util.List;

public class Route {
	private List<Path> paths;
	private String startLoc;
	private String endLoc;
	private int counter;
	
	public Route(String start, String end)
	{
		this.startLoc = start;
		this.endLoc = end;
		this.paths = new ArrayList<Path>();
		counter = 0;
	}
	public void addPath(Path newPath)
	{
		this.paths.add(newPath);
	}
	public List<Path> getPaths()
	{
		return this.paths;
	}
	public Path getFirstPath()
	{
		if (this.paths.size() == 0)
		{
			return null;
		}
		counter = 1;
		return this.paths.get(0);
	}
	public Path next()
	{
		Path returned = null;
		if (counter < this.paths.size())
		{
			returned = this.paths.get(counter);
			counter++;
		}
		return returned;
	}
	public String getStartLoc()
	{
		return this.startLoc;
	}
	public String getEndLoc()
	{
		return this.endLoc;
	}
}
