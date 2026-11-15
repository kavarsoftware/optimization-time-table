package edu.timetable;
import java.nio.file.*;
import java.io.*;
import java.util.*;
public class SheetExport {
    static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\n","<br>");}
    static void save(Data d,SearchEngine s,Path file)throws IOException{
        var layout=new WeekGrid(d,s);var cells=new ArrayList<>(layout.cells);cells.sort(Comparator.comparingInt(WeekGrid.Cell::row).thenComparingInt(WeekGrid.Cell::col));
        StringBuilder html=new StringBuilder("<!doctype html><meta charset=utf-8><title>Department timetable</title><style>"+SimpleExport.htmlCss()+"</style><h1>");
        html.append(escape(layout.title())).append("</h1><p>").append(escape(s.status));
        if(ScheduleObjective.goal(s.m)!=null)html.append(" · ").append(escape(ScheduleObjective.goal(s.m).toString())).append(" · score (soft.hard) ").append(s.scoreText());
        else html.append(" · weighted objective ").append(s.score);
        html.append(" · zero hard violations</p><table>");
        int row=-1;for(var c:cells){if(c.row()!=row){if(row>=0)html.append("</tr>");html.append("<tr>");row=c.row();}
            html.append("<td class='").append(c.style()).append("' colspan='").append(c.width()).append("' rowspan='").append(c.height()).append("'>").append(escape(c.text())).append("</td>");}
        Files.writeString(file,html.append("</tr></table>").toString());
    }
    public static void main(String[] args)throws Exception{
        Data d=Data.load(Path.of("examples/fall-demo.week"));ProblemData m=new ProblemData(d);
        SearchEngine s=new SearchEngine(m,new SearchEngine.Settings(7,15,12,40,.12,15000),()->false);
        s.run("Random",null);if(s.best==null||!Evaluator.evaluate(m,s.best).valid())throw new AssertionError("No valid result");
        var grid=new WeekGrid(d,s);
        if(grid.cells.stream().noneMatch(c->c.event()>=0&&c.width()==2))throw new AssertionError("Missing joint lecture");
        Path tmp=Files.createTempFile("semester",".week");d.save(tmp);if(!Arrays.equals(Data.load(tmp).rows.get("Institution").getFirst(),d.rows.get("Institution").getFirst()))throw new AssertionError("Semester metadata lost");Files.delete(tmp);
        save(d,s,Path.of("Fall-Semester-Timetable.html"));
        System.out.println("Events: "+m.events.size()+"; groups: "+m.stages.size()+"; gap: "+s.score+"; evaluated: "+s.evaluations+"; "+s.status);
    }
}
