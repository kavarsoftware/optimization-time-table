package edu.timetable;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
/** Expands the weekly pattern into dated local-time events, excluding holidays and exams. */
final class CalendarExport {
    static String escape(String s){return s.replace("\\","\\\\").replace("\r","").replace("\n","\\n").replace(";","\\;").replace(",","\\,");}
    static String folded(String line){
        var out=new StringBuilder();int bytes=0;
        for(int cp:line.codePoints().toArray()){
            String c=new String(Character.toChars(cp));int length=c.getBytes(StandardCharsets.UTF_8).length;
            if(bytes+length>75){out.append("\r\n ");bytes=1;}out.append(c);bytes+=length;
        }return out+"\r\n";
    }
    static void save(Data data,SearchEngine result,Path path)throws IOException{
        var dates=TermCalendar.dates(data);ProblemData.require(!dates.isEmpty(),"Set semester start/end dates and teaching days before calendar export.");
        var lines=new ArrayList<>(List.of("BEGIN:VCALENDAR","VERSION:2.0","PRODID:-//UniPlan5//Timetable//EN","CALSCALE:GREGORIAN"));
        var format=DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");String stamp=DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(Instant.now());
        for(var date:dates)for(int i=0;i<result.best.length;i++){
            var e=result.m.events.get(i);var p=result.m.choices.get(i).get(result.best[i]);
            if(!date.getDayOfWeek().name().equalsIgnoreCase(p.day()))continue;
            var start=date.atStartOfDay().plusMinutes(p.start());
            String identity=data.rows.get("Campus").getFirst()[0]+"/"+data.rows.get("Campus").getFirst()[1]+"/"+date+"/"+i;
            lines.addAll(List.of("BEGIN:VEVENT","UID:"+UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8))+"@uniplan5","DTSTAMP:"+stamp,"DTSTART:"+format.format(start),"DTEND:"+format.format(start.plusMinutes(e.span())),"SUMMARY:"+escape(e.name()),"LOCATION:"+escape(p.room()),"DESCRIPTION:"+escape(e.teacher()+" / "+String.join(",",e.groups())),"END:VEVENT"));
        }
        lines.add("END:VCALENDAR");var content=new StringBuilder();lines.forEach(l->content.append(folded(l)));Files.writeString(path,content,StandardCharsets.UTF_8);
    }
}
