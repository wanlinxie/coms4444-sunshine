package sunshine.g8;

import java.lang.Math;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.HashMap;
import java.util.Comparator;

import java.io.*;

import sunshine.sim.Command;
import sunshine.sim.Tractor;
import sunshine.sim.CommandType;
import sunshine.sim.Point;
import sunshine.sim.Trailer;


public class Player implements sunshine.sim.Player 
{
	// Random seed of 42.
	private int seed = 42;
	private Random rand;
	private final static Point BARN= new Point(0.0, 0.0);
	private final static double DISTANCE_CUTOFF = 300;

	// List of Bales, one batch for trailers and the other for tractor ONLY
	private List<Point> tractor_bales;
	private List<Point> trailer_bales;
    private List<Point> total_bales;

	// Make list of centroids, Deposit points for the trailer
	private List<Point> centroids = new ArrayList<Point>();

	// Map the TractorID (matched with trailer it is matched to...to Location of Trailer
	private HashMap<Integer, Point> trailer_map = new HashMap<Integer, Point>(); 
	private HashMap<Integer, Integer> trailer_num = new HashMap<Integer, Integer>();

	private HashMap<Integer, List<Point>> taskList = new HashMap<Integer, List<Point>>();

	// Get number of tractors, change stategy as needed
	private int n_tractors = 0;
	private double dimensions = 0;
	
	// Collect Data...Average Distance in each cluster!
	private ArrayList<Double> cluster_distances = new ArrayList<Double>();
	private ArrayList<Double> distance_to_barn = new ArrayList<Double>();

	public Player() 
	{
		rand = new Random(seed);
	}

	private double distance(Point p1, Point p2) 
	{
        return Math.sqrt(Math.pow(p1.x-p2.x,2)+Math.pow(p1.y-p2.y,2));
	}

	// Purpose: Sort location of bales. Closest to furthest bale
	public Comparator<Point> pointComparator = new Comparator<Point>() 
	{	
            public int compare(Point p1, Point p2) 	
            {
                double d1 = distance(BARN, p1);
                double d2 = distance(BARN, p2);
                if (d1 < d2) 
                {
                    return -1;
                } 
                else if (d1 > d2) 
                {
                    return 1;
                } 
                else 
                {
                    return 0;
                }
            }
	};

	// Will break a list into a list<list>, maxmimize all lists up to n.
	// The last list may be size n or less
	public ArrayList<List<Point>> split_list_n(List<Point> full, int n)
	{
            ArrayList<List<Point>> split = new ArrayList<List<Point>>();
            for (int i = 0; i < full.size();i+=n)
            {
                List<Point> chunk = new ArrayList<Point>();
                for (int j = i; j < n; j++)
                {
                    // Edge case: last batch has less than 11 bales
                    if(j >= full.size())	
                    {
                        return split;
                    }
                    chunk.add(full.get(j));
                }
                split.add(chunk);
            }
            return split;
	}

	// Split the Tractor and Trailer bale depending on the index.
	public void split_trailer_tractor_batch(List<Point> bales, double split)
	{
            if(split == 1)
            {
                tractor_bales = bales;
                trailer_bales = new ArrayList<Point>();
                return;
            }
            else if(split == 0)
            {
                tractor_bales = new ArrayList<Point>();
                trailer_bales = bales;
                return;
            }
            // split determines the fraction going to tractor.
            // e.g 1/2 means 1/2 tractor 1/2 trailer
            // e.g 1/3 means 1/3 tractor and 2/3 trailer
            else
            {
                //System.err.println(bales.size());
                int split_idx = (int) (bales.size() * split);
                trailer_bales = new ArrayList<>(bales.subList(split_idx, bales.size()));
                tractor_bales = new ArrayList<>(bales.subList(0, split_idx));
                System.err.println("INIT Bale size: " + bales.size());
                System.err.println("INIT Trailer size: " + trailer_bales.size());
                System.err.println("INIT Tractor size: " + tractor_bales.size());
            }
        }

	public void init(List<Point> bales, int n, double m, double t)
	{
            // Organize how bales will be selected by tractors
            // Min to max distance
            Collections.sort(bales, pointComparator);
            // Get distance of each bale from the barn
            for (int i = 0; i < bales.size(); i++) 
            {
                distance_to_barn.add(distance(BARN, bales.get(i)));
            }
            int cutoff = cutoff_idx(distance_to_barn);
            
            // DECIDE YOUR PERCENTILE SPLIT
            
            total_bales = bales;
            // 1- 1/2 closest to tractor
            // 2- 1/2 closest to trailer
            // input 1 = Use GREEDY oNLY
            // input 0 = Use Trailer ONLY
            split_trailer_tractor_batch(bales, (double) cutoff/bales.size());
            for (int i = 0; i < n; i++) 
            {
                trailer_map.put(i, BARN);
                trailer_num.put(i, 0);
                taskList.put(i, new ArrayList<Point>());
            }
            this.n_tractors = n;
            this.dimensions = m;
        }

	// OPTON 1: USE NO TRAILER, for use with 1 Tractor
	public Command trailer_greedy(Tractor tractor)
	{
			/*
            System.err.println("Total Bales: " + total_bales.size());
            System.err.println("Trailer: " + trailer_bales.size());
            System.err.println("Tractor: " + tractor_bales.size());
		 	*/    
            // At this Point I know the Trailer Bale List is empty
            // There is also NO Remaining Tasks!
            // BUT THERE ARE TRAILERS WITH SOME BALES IN THEM. WE MUST FACTOR THIS IN

            // Is the tractor at the barn
            if(tractor.getLocation().equals(BARN))
            {
            	if (tractor.getHasBale()) 
        		{
        			return new Command(CommandType.UNLOAD);	
        		}
            	else
            	{
            		// Detach any trailer to allow unstacking!
            		if(tractor.getAttachedTrailer() != null)
                	{
            			trailer_map.put(tractor.getId(), tractor.getLocation());
                		return new Command(CommandType.DETATCH);
                	}
            		// There is no trailer!
            		else 
            		{
            			Point p;
            			// IS YOUR TRAILER WITH YOU NOW OR NOT?
            			p = trailer_map.get(tractor.getId());
            			if(!p.equals(BARN))
            			{
            				return Command.createMoveCommand(p);
            			}
            			
            			// First priority: UNSTACK!
            			if(trailer_num.get(tractor.getId()) > 0)
            			{
            				//update hashmap
            				trailer_num.put(tractor.getId(), trailer_num.get(tractor.getId()) - 1);
            				return new Command(CommandType.UNSTACK);
            			}
            			// Time to look for another bale!
            			else
            			{
            				// Empty out tasklist
            				if(taskList.get(tractor.getId()).size() > 0)
            				{
            					p = taskList.get(tractor.getId()).remove(0);
            					return Command.createMoveCommand(p);
            				}
            				else
            				{
            					if(!tractor_bales.isEmpty())
            					{
                					p = tractor_bales.remove(tractor_bales.size() - 1);
                        			return Command.createMoveCommand(p);	
            					}
            					// TERMINATE!
            					else
            					{
            						System.out.println("In Trailers: " + in_trailer());
                                	System.out.println("In Task List: " + taskList.get(tractor.getId()).size());
                                	System.out.println("In tractor bale: " + tractor_bales.size());
                                	finish();
                    				return new Command(CommandType.UNSTACK);
            					}
            				}
            			}
            		}
            	}
            }
            // Trailer and/or tractor NOT in barn
            else
            {
            	// I don't care! COME BACK NOW!
            	if(tractor.getAttachedTrailer() != null)
            	{
            		return Command.createMoveCommand(BARN);
            	}
            	// tractor is not attached, but does its trailer have bales?
            	else
            	{
            		// If on trailer location, attach and GO!
            		if(trailer_map.get(tractor.getId()).equals(tractor.getLocation()))
            		{
            			return new Command(CommandType.ATTACH);
            		}
            		else
            		{
            			if(tractor.getHasBale())
                    	{
                    		return Command.createMoveCommand(BARN);
                    	}
                    	else
                    	{
                        	return new Command(CommandType.LOAD);
                    	}
            		}
            	}
            }
	}

	private int getMaxIdx(List<Double> ptlist) 
	{
		double max = 0;
		int maxindex = 0;
		for (int i=0;i<ptlist.size();i++) 
		{
			if (ptlist.get(i) > max) 
			{
				max = ptlist.get(i);
				maxindex = i;
			}
		}
		return maxindex;
	}

	private List<Point> closestTen(Point x, List<Point> currBales) 
	{
		List<Point> pos = new ArrayList<Point>();
		List<Double> dist = new ArrayList<Double>();
		int k = 0;
		double max = 0;

		for (Point p: currBales) 
		{
			if(k < 10) 
			{
				double temp = distance(x,p);
				pos.add(p);
				dist.add(temp);
				k++;
			}
			else 
			{
				double temp = distance(x, p);
				int i = getMaxIdx(dist);
				max = dist.get(i);
				if (max > temp) 
				{
					dist.remove(i);
					pos.remove(i);
					dist.add(temp);
					pos.add(p);
				}
			}
		}
		// Get the Distances!
		cluster_distances.add(mean(dist));
		return pos;
	}

	// Do math to get point within 1 meter
	// Goal: get closer to POINT TO 
	private Point optimalPoint(Point to, Point from, double radius)
	{
            Point res = new Point(0,0); 
            double mag = Math.sqrt((to.x * to.x) + (to.y * to.y));
            res.x = to.x - (radius * (to.x/mag)); 
            res.y = to.y - (radius * (to.y/mag)); 
            return res; 
	}

    // Step 1- Empty out TRAILER BALES
    // Step 2- Empty out TRACTOR BALES
	public Command getCommand(Tractor tractor)
	{
		//System.err.println("Trailer: " + trailer_bales.size());
        //System.err.println("Tractor: " + tractor_bales.size());

		// ONLY SWITCH IS NO TRAILER BALES LEFT IN TASK LIST OR BALE ARRAY!    
		if(trailer_bales.isEmpty() && taskList.get(tractor.getId()).size() == 0)
        {
			System.out.println("Sink 1: ---done---");
			return trailer_greedy(tractor);
        }
        
                
        // If you are at the barn and have no task...
		if (taskList.get(tractor.getId()).size() == 0 && tractor.getLocation().equals(BARN))
		{
			// Empty list right now
			// Pick farthest point
			if (trailer_bales.isEmpty())
			{
                   System.out.println("Sink 2: ---done---");
                   return trailer_greedy(tractor);
            }
			else if (trailer_bales.size() <= 11) 
			{
				List<Point> tasks = new ArrayList<Point>();
				for(int i = 0;i < trailer_bales.size();i++)
				{	
                     tasks.add(trailer_bales.remove(i));
				}
				Collections.sort(tasks, pointComparator);
				taskList.put(tractor.getId(),tasks);
			}
			else 
			{
				Point p = trailer_bales.remove(trailer_bales.size()-1);
				List<Point> tasks = closestTen(p, trailer_bales);
				for (int i = 0; i < tasks.size();i++) 
				{	
                     trailer_bales.remove(trailer_bales.indexOf(tasks.get(i)));
				}
				tasks.add(p);
				//sort tasks by distance to BARN,
				Collections.sort(tasks, pointComparator);
				taskList.put(tractor.getId(),tasks);
			}
		}
		//when tractor is in barn
		if (tractor.getLocation().equals(BARN)) 
		{
			//if at barn and has bale, always unload
			if (tractor.getHasBale()) 
			{	
                  return new Command(CommandType.UNLOAD);
			}
			else if (tractor.getAttachedTrailer() != null) //trailer
			{
				//if trailer has nothing
				if(tractor.getAttachedTrailer().getNumBales() == 0) 
				{
					/*if (tractor.getHasBale()) {
						return new Command(CommandType.UNLOAD);
					}*/
					//either move if 
					if ((taskList.get(tractor.getId()).size()) > 0) 
					{
						//tractor has tasks
						Point p = taskList.get(tractor.getId()).get(0);
						//TODO
						//do a function here, that optimizes p 
						//closest to barn and one m away from bale

						//Point o = optimalPoint(p); 
						return Command.createMoveCommand(p);
                    }
                    // THIS LINE NEVER HAPPENS
					else 
					{
						System.err.println("Reaching here should be impossible...");
                        return trailer_greedy(tractor);
					}
				}
				else 
				{ 
					//trailer is attached, trailer has bales
					return new Command(CommandType.DETATCH);
				}
			}
			else //no trailer
			{
				if(trailer_num.get(tractor.getId()) != 0) 
				{ 
					//detached trailer has bales
					if (tractor.getHasBale()) 
					{ 
						//probably won't
						return new Command(CommandType.UNLOAD);
					} 
					else 
					{
						trailer_num.put(tractor.getId(),trailer_num.get(tractor.getId())-1); //update hashmap
						return new Command(CommandType.UNSTACK);
					}
				} 
				else 
				{
					//detached trailer has 0 bales
					if (tractor.getHasBale()) 
					{
						return new Command(CommandType.UNLOAD);//no op
					}
					else 
					{
						return new Command(CommandType.ATTACH);
					}
				}
				tasks.add(p);
				//sort tasks by distance to BARN,
				Collections.sort(tasks, pointComparator);
				taskList.put(tractor.getId(),tasks);
			}
		}
		// There is no bale!
		// not at barn
		else
		{
			if (tractor.getAttachedTrailer() != null) //trailer attached, only attach with bale in forklift
			{
				//nothing in trailer have things to do 
				if (tractor.getAttachedTrailer().getNumBales() == 0 && taskList.get(tractor.getId()).size() != 0) 
				{
					//nothing in trailer
					trailer_map.put(tractor.getId(),tractor.getLocation());
					return new Command(CommandType.DETATCH);
				}
				else 
				{
					// Something in trailer, ready to move (should be 10)  //finished all tasks, ready to move to barn
					trailer_map.put(tractor.getId(), BARN);
					return Command.createMoveCommand(BARN);
				}
			}
			// no trailer attached
			else 
			{
				Point trail_loc = trailer_map.get(tractor.getId()); //location of the trailer
				if (taskList.get(tractor.getId()).size() > 0) 
				{
					//something to do in the tasklist
					//if trailer has less than 10 bales on it 
					if (trailer_num.get(tractor.getId()) < 10) 
					{
						//if forklift has bale
						if (tractor.getHasBale()) 
						{
							//if tractor is by the trailer 
							//TODO: or is in range 
							if (tractor.getLocation().equals(trail_loc)) 
							{
								//updating trailer bales hashmap
								trailer_num.put(tractor.getId(),trailer_num.get(tractor.getId())+1);
								return new Command(CommandType.STACK);
							}
							else 
							{ 
								//move to trailer
								return Command.createMoveCommand(trail_loc);
							}
						} 
						//forklift doesn't have bale, go to next in task list
						else 
						{ 
							// no bale, need to go to next bale 
							Point p = taskList.get(tractor.getId()).get(0);
							//if you happen to already be there, load 
							// TODO
							//OR IF LOCATION IS WITHIN ONE METER
							if (tractor.getLocation().equals(p)) 
							{
								taskList.get(tractor.getId()).remove(0);
								//trailer_bales.remove(trailer_bales.size()-1);
								return new Command(CommandType.LOAD);
							}
							else 
							{
								return Command.createMoveCommand(p);
							}
						}
					} 
					else 
					{
						//trailer is full, tractor must load
						Point p = taskList.get(tractor.getId()).get(0);
						if (tractor.getLocation().equals(p)) 
						{
							taskList.get(tractor.getId()).remove(0);
							//trailer_bales.remove(trailer_bales.size()-1);
							return new Command(CommandType.LOAD);
						}
						else 
						{
							return Command.createMoveCommand(p);
						}
					}
				} 
				else 
				{ 
					//tasklist done
					//move back to where trailer is 
					if (!tractor.getLocation().equals(trail_loc)) 
					{
						//Point o = optimalPoint(trail_loc); 
						return Command.createMoveCommand(trail_loc);
					}
					//at trailer
					else 
					{
						return new Command(CommandType.ATTACH);
					}
				}
			}
		}
	}
	
	// Debugging
	public int sum(List<Integer> e)
	{
		int sum = 0;
		for (int i = 0; i < e.size();i++)
		{
			sum += e.get(i).intValue();
		}
		return sum;
	}
	
	public Double mean(List<Double> d)
	{
		Double mean = new Double(0.9);
		for(int i = 0; i < d.size();i++)
		{
			mean += d.get(i);
		}
		return mean/d.size();
	}
	
	public int sum(Integer [] e)
	{
		int sum = 0;
		for (int i = 0; i < e.length;i++)
		{
			sum += e[i].intValue();
		}
		return sum;
	}
	
	// Get sum of bales right now in trailer
	public int in_trailer()
	{
		Integer [] remainder = trailer_num.values().toArray(new Integer[trailer_num.size()]);
		return sum(remainder);
	}
	
	// If there are loose trailers with bales...grab them and return to the barn!
	public Point trailer_cleanup()
	{
		Point grab = null;
		Point [] remainder = trailer_map.values().toArray(new Point[trailer_map.size()]);
		for (int i = 0; i < remainder.length; i++)
		{
			if(!remainder[i].equals(BARN))
			{
				grab = remainder[i];
				return grab;
			}
		}
		return grab;
	}

	// Print results of cluster distances
	public void finish()
	{
		Writer writer = null;
		try
		{
			writer = new BufferedWriter(new OutputStreamWriter(
		              new FileOutputStream("cluster.txt"), "utf-8"));
			for (int i = 0; i < cluster_distances.size();i++)
			{
				writer.write(cluster_distances.get(i)+",");
				System.out.print(cluster_distances.get(i)+",");
			}
			System.out.println("Distances");
			for (int i = 0; i < distance_to_barn.size();i++)
			{
				System.out.print(distance_to_barn.get(i)+",");
			}
			writer.close();
		}
		catch(Exception e)
		{
			
		}
	}
	
	public int cutoff_idx(List<Double> distance)
	{
		// I KNOW IT IS SORTED ALREADY! MIN TO HIGH
		int i = 0;
		for (; i < distance.size();i++)
		{
			if(distance.get(i) > DISTANCE_CUTOFF)
			{
				return i;
			}
		}
		return i;
	}
}
