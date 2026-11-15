package edu.timetable;
import com.google.ortools.Loader;
import com.sun.jna.Platform;
import java.nio.file.Path;
/** Loads the retained OR-Tools binaries from Maven output directories or a packaged JAR. */
final class NativeSolver {
    private static boolean loaded;
    static synchronized void load(){
        if(loaded)return;
        String name="ortools-"+Platform.RESOURCE_PREFIX+"/"+System.mapLibraryName("jniortools");
        var resource=NativeSolver.class.getClassLoader().getResource(name);
        if(resource==null)throw new IllegalStateException("Native solver is missing. Run mvn process-resources for this computer's operating system.");
        try{
            if(resource.getProtocol().equals("file"))System.load(Path.of(resource.toURI()).toAbsolutePath().toString());
            else Loader.loadNativeLibraries();
            loaded=true;
        }catch(Exception ex){throw new IllegalStateException("Cannot load native solver for "+Platform.RESOURCE_PREFIX,ex);}
    }
}
