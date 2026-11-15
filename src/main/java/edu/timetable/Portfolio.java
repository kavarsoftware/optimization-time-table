package edu.timetable;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** One archive keeps multiple institution/semester datasets without mixing their resources. */
class Portfolio {
    final Map<String,Data> entries=new LinkedHashMap<>();
    static String key(Data d){
        var c=d.rows.get("Campus").getFirst();var institution=d.rows.get("Institution");
        return c[0]+" / "+(institution.isEmpty()?"College":institution.getFirst()[0])+" / "+c[1]+" / "+c[2];
    }
    void put(Data d){entries.put(key(d),d);}
    void save(Path file)throws IOException{
        Path temp=Files.createTempFile(file.toAbsolutePath().getParent(),"planner-", ".tmp");
        try{
            Files.writeString(temp,JsonStorage.writeProject(this));
            Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temp);}
    }
    static Portfolio load(Path file)throws IOException{
        if(Files.readString(file,java.nio.charset.StandardCharsets.ISO_8859_1).stripLeading().startsWith("{"))return JsonStorage.readProject(Files.readString(file));
        var out=new Portfolio();try(var zip=new ZipInputStream(Files.newInputStream(file))){
            ZipEntry e;while((e=zip.getNextEntry())!=null)if(!e.isDirectory())out.put(Data.parse(new String(zip.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)));
        }
        if(out.entries.isEmpty())throw new IOException("This project has no semesters.");return out;
    }
}
