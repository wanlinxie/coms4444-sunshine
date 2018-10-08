package sunshine.g8;

import java.lang.Math;


import java.util.List;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.HashMap;
import java.util.Comparator;

import sunshine.sim.Command;
import sunshine.sim.Tractor;
import sunshine.sim.CommandType;
import sunshine.sim.Point;
import sunshine.sim.Trailer;


public class Player implements sunshine.sim.Player {
    // Random seed of 42.
    private int seed = 42;
    private Random rand;

    
    List<Point> bales;
    //Point origin;

    private boolean toggle = true; 
    private boolean sent = false; 

    public Player() {
        rand = new Random(seed);
    }
    private double distance(Point p1, Point p2) {
        return Math.sqrt(Math.pow(p1.x-p2.x,2)+Math.pow(p1.y-p2.y,2));

    }
    Comparator<Point> pointComparator = new Comparator<Point>() {
        @Override
        public int compare(Point p1, Point p2) {
            Point origin = new Point(0.0,0.0);
            double d1 = distance(origin, p1);
            double d2 = distance(origin, p2);
            if (d1 < d2) {
                return -1;
            } else if (d1 > d2) {
                return 1;
            } else {
                return 0;
            }
        }
    };

    
    public void init(List<Point> bales, int n, double m, double t)
    {

        Collections.sort(bales,pointComparator);
        this.bales = bales;

        //System.out.println(n); //30 - number of tractors
        //System.out.println(m); //500 - length of field
        //System.out.println(t); //10000 - time

        int cellSize = 200; //TODO


    }

    private void doToggle(){
        if(toggle){
            toggle = false; 
        }
        else{
            toggle = true; 
        }
    }
    
    public Command getCommand(Tractor tractor)
    {
        //tractor is at orgin
        if(tractor.getLocation().equals(new Point(0.0, 0.0))){

            

            //if has trailer 
            if(tractor.getAttachedTrailer() != null){
                
                Trailer trailer = trailer = tractor.getAttachedTrailer(); 

                //half the time remove trailer if its empty 
                if(toggle && trailer.getNumBales() == 0){
                    doToggle(); 
                    return new Command(CommandType.DETATCH); 
                }
                //if trailer is full 
                if(trailer.getNumBales() == 10 && tractor.getHasBale()){
                    return new Command(CommandType.UNLOAD);
                } 
                //
                while(trailer.getNumBales() > 0){
                    return new Command(CommandType.UNSTACK);
                }

                //if empty trailer go far 
                if(trailer.getNumBales() == 0){
                    Point p = bales.remove(bales.size()-1);
                    doToggle(); 
                    return Command.createMoveCommand(p);
                }

            }
            //if at origin without trailer go near 
            if(tractor.getAttachedTrailer() == null){
                Point p = bales.remove(1);
                doToggle(); 
                return Command.createMoveCommand(p);
            }   

        }

        //tractor not at origin 
        else{

            //if it has trailer
            if(tractor.getAttachedTrailer() != null){
                Trailer trailer = tractor.getAttachedTrailer(); 

                //TODO: Problem is I need to have groupings sent out 
                //if zero bales, just sent out so stack 
                if(trailer.getNumBales() == 0){
                    System.out.println("Adding my first bale to trailer!"); 
                    return new Command(CommandType.LOAD);
                }
                //TODO: need some way to check if something is near enoihg to stack 
                //if fewer than 10 bales,
                if(trailer.getNumBales() < 10  && !sent){
                    Point p = bales.remove(bales.size()-1);
                    sent = true; 
                    return Command.createMoveCommand(p); 
                }
                //if fewer then 10 bales and it was just sent somewhere stack 
                if(trailer.getNumBales() < 10  && sent){
                    sent = false; 
                    return new Command(CommandType.STACK); 
                }


                if(trailer.getNumBales() == 10){

                    if(!tractor.getHasBale() && !sent){
                        Point p = bales.remove(bales.size()-1);
                        sent = true; 
                        return Command.createMoveCommand(p); 
                    }
        
                    if(!tractor.getHasBale() && sent){
                        return new Command(CommandType.LOAD); 
                    }
                 }
                 //else send back to origin
                 else{
                    System.out.println("Heading back!"); 
                    return Command.createMoveCommand(new Point(0.0, 0.0)); 
                 }



            }

            //if no trailer and no hay bale, stack 
            if(!tractor.getHasBale()){
                return new Command(CommandType.STACK); 
            }
            //if no trailer, and has bale return to origin 
            else{
                 return Command.createMoveCommand(new Point(0.0, 0.0));
            }

        }
        return new Command(CommandType.DETATCH); 

    }

        /*
        if (tractor.getHasBale()) {
            if (tractor.getLocation().equals(new Point(0.0, 0.0))) {
                return new Command(CommandType.UNLOAD);
            } else {
                return Command.createMoveCommand(new Point(0.0, 0.0));
            }
        } else { // no bale
            if (tractor.getLocation().equals(new Point(0.0, 0.0))) {
                
                // has trailor 
                if(toggle && tractor.getAttachedTrailer() != null){
                    doToggle(); 
                    System.out.
                    Point far = bales.remove(0);
                    return Command.createMoveCommand(far);
                }

                //doesn't have trailor 
                if (tractor.getAttachedTrailer() == null) {
                    Point p = bales.remove(bales.size()-1);
                    return Command.createMoveCommand(p);
                } else {
                    doToggle(); 
                    return new Command(CommandType.DETATCH);
                }

            } else { //tractor not at origin 
                return new Command(CommandType.LOAD);
            }

        }

    }
        */
}
