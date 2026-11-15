package edu.timetable;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;
/** Export styling derives its colors from the same palette as JavaFX. */
final class SimpleExport {
    static String htmlCss()throws IOException{
        String css=new String(SimpleExport.class.getResourceAsStream("/studio.css").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        var vars=new StringBuilder(":root{");var match=Pattern.compile("-([a-z]+):\\s*(#[0-9a-fA-F]{6})").matcher(css);while(match.find())vars.append("--").append(match.group(1)).append(':').append(match.group(2)).append(';');vars.append('}');
        return vars+"*{box-sizing:border-box}body{background:var(--page);color:var(--ink);font:14px sans-serif;margin:24px}h1{font-size:22px;color:var(--forest)}table{border-spacing:2px;width:100%;font-size:11px}td{padding:6px;text-align:center;border-radius:3px;background:var(--page)}.heading,.day{background:var(--forest);color:var(--page);font-weight:bold}.hour,.lecture{background:var(--mint)}.lab0{background:var(--teal)}.lab1{background:var(--lime)}.lab2{background:var(--warning)}.closure{background:var(--grey)}@page{size:A3 landscape;margin:8mm}@media print{body{margin:0}td{padding:3px}*{print-color-adjust:exact}}";
    }
    static String escape(String value){return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    static String printHtml(SearchEngine search,boolean byStage,String selected)throws IOException{
        var sections=new TreeSet<String>();for(var e:search.m.events)if(byStage){for(String group:e.groups())sections.add(search.m.stages.get(group));}else sections.add(e.teacher());
        var html=new StringBuilder("<!doctype html><html><head><meta charset='UTF-8'><title>UniPlan Print / Save as PDF</title><style>").append(htmlCss()).append("@page{size:A4 landscape;margin:10mm}.print-page{break-after:page;page-break-after:always}.print-page:last-child{break-after:auto;page-break-after:auto}th{background:var(--forest);color:var(--page);padding:7px}tr:nth-child(even) td{background:var(--mint)}.print-hint{margin:16px}@media print{.print-hint{display:none}tr{break-inside:avoid}}</style></head><body><p class='print-hint'>Use your browser's Print command and choose Save as PDF. Landscape layout; one page per ").append(byStage?"stage":"instructor").append(".</p>");
        for(String section:sections){if(!selected.equals("All")&&!selected.equals(section))continue;html.append("<section class='print-page'><h1>").append(escape(section.startsWith("__unassigned_")?"Unassigned instructor":section)).append("</h1>");
            if(ScheduleObjective.goal(search.m)!=null)html.append("<p>").append(escape(ScheduleObjective.goal(search.m).toString())).append(" · score (soft.hard) ").append(search.scoreText()).append("</p>");
            html.append("<table><thead><tr><th>Day</th><th>Start</th><th>Finish</th><th>Subject</th><th>Instructor</th><th>Groups</th><th>Room</th></tr></thead><tbody>");
            var order=new ArrayList<Integer>();for(int i=0;i<search.m.events.size();i++)order.add(i);order.sort(Comparator.comparingInt((Integer i)->search.m.days.indexOf(search.m.choices.get(i).get(search.best[i]).day())).thenComparingInt(i->search.m.choices.get(i).get(search.best[i]).start()));
            for(int i:order){var e=search.m.events.get(i);if(byStage?!e.groups().stream().anyMatch(g->section.equals(search.m.stages.get(g))):!section.equals(e.teacher()))continue;var p=search.m.choices.get(i).get(search.best[i]);html.append("<tr>");for(String value:List.of(p.day(),Data.clock(p.start()),Data.clock(p.start()+e.span()),e.name(),e.teacher().startsWith("__unassigned_")?"Not assigned":e.teacher(),String.join(", ",e.groups()),p.room()))html.append("<td>").append(escape(value)).append("</td>");html.append("</tr>");}html.append("</tbody></table></section>");
        }return html.append("</body></html>").toString();
    }
    static void csv(Path file,List<String[]> rows)throws IOException{
        try(var out=Files.newBufferedWriter(file)){out.write("Day,Start,Finish,Subject,Instructor,Groups,Room\n");for(var row:rows){var escaped=new ArrayList<String>();for(String value:row)escaped.add("\""+value.replace("\"","\"\"")+"\"");out.write(String.join(",",escaped)+"\n");}}
    }
}
