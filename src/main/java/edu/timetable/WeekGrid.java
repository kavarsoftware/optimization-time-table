package edu.timetable;
import java.util.*;
import static edu.timetable.Data.*;
/** Shared layout for the reference-style week view and export. */
class WeekGrid {
    record Cell(int col,int row,int width,int height,String text,String style,int event){}
    final List<Cell> cells=new ArrayList<>();
    final Data data;
    String title(){
        var c=data.rows.get("Campus").getFirst();var meta=data.rows.get("Institution");
        return c[0]+"  /  "+(meta.isEmpty()?"":meta.getFirst()[0]+"  /  ")+c[1]+"   ·   "+c[2];
    }
    void cell(int col,int row,int w,int h,String text,String style,int event){cells.add(new Cell(col,row,w,h,text,style,event));}
    WeekGrid(Data data,SearchEngine search){this(data,search,new ArrayList<>(search.m.stages.keySet()));}
    WeekGrid(Data data,SearchEngine search,List<String> shownGroups){
        this.data=data;var m=search.m;var groups=new ArrayList<>(shownGroups);
        cell(0,0,1,1,"Day","heading",-1);cell(1,0,1,1,"Time","heading",-1);
        for(int j=0;j<groups.size();j++){var g=groups.get(j);cell(j+2,0,1,1,m.stages.get(g)+" · "+g,"heading",-1);}
        var types=new LinkedHashMap<String,Integer>();for(var r:data.rows.get("Rooms"))if(!r[1].equalsIgnoreCase("Lecture"))types.put(r[0],types.size());int base=1;
        for(var day:data.rows.get("Days")){
            int open=minute(day[1]),close=minute(day[2]);var marks=new TreeSet<Integer>();marks.add(close);
            for(int t=open;t<close;t+=60)marks.add(t);
            for(int i=0;i<m.events.size();i++){var p=m.choices.get(i).get(search.best[i]);if(day[0].equals(p.day())){marks.add(p.start());marks.add(p.start()+m.events.get(i).span());}}
            for(var p:m.periods)if(p[0].equals(day[0])){marks.add(minute(p[2]));marks.add(minute(p[3]));}
            var times=new ArrayList<>(marks);int rows=times.size()-1;
            cell(0,base,1,rows,day[0],"day",-1);
            boolean[][] occupied=new boolean[rows][groups.size()];
            for(int i=0;i<m.events.size();i++){
                var p=m.choices.get(i).get(search.best[i]);if(!p.day().equals(day[0]))continue;
                var e=m.events.get(i);int r=times.indexOf(p.start()),end=times.indexOf(p.start()+e.span());
                for(int j=0;j<groups.size();){
                    if(!e.groups().contains(groups.get(j))){j++;continue;}
                    int k=j+1;while(k<groups.size()&&e.groups().contains(groups.get(k)))k++;
                    for(int y=r;y<end;y++)Arrays.fill(occupied[y],j,k,true);
                    String style=e.type().equalsIgnoreCase("Lecture")?"lecture":"lab"+types.computeIfAbsent(p.room(),key->types.size())%3;
                    cell(j+2,base+r,k-j,end-r,e.name()+"\n"+(e.teacher().startsWith("__unassigned_")?"Not assigned":e.teacher())+" · "+p.room()+"\n"+clock(p.start())+"–"+clock(p.start()+e.span()),style,i);j=k;
                }
            }
            for(int r=0;r<rows;r++){cell(1,base+r,1,1,clock(times.get(r)),"hour",-1);for(int j=0;j<groups.size();j++)if(!occupied[r][j]){
                    String text="",style="empty";
                    for(var p:m.periods)if(p[0].equals(day[0])&&!p[4].equals("Teaching")&&minute(p[2])<=times.get(r)&&times.get(r)<minute(p[3])){text=p[1]+" · "+p[4];style="closure";}
                    cell(j+2,base+r,1,1,text,style,-1);
                }}
            base+=rows;
        }
    }
}
