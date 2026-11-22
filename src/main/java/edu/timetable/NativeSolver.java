package edu.timetable;

import com.google.ortools.Loader;
import com.sun.jna.Platform;
import java.net.URISyntaxException;
import java.nio.file.Path;

/** Loads the OR-Tools libraries from the platform-specific runtime dependency. */
final class NativeSolver {
    private static boolean loaded;

    static synchronized void load(){
        if(loaded)return;
        try{
            String name="ortools-"+Platform.RESOURCE_PREFIX+"/"+System.mapLibraryName("jniortools");
            var resource=NativeSolver.class.getClassLoader().getResource(name);
            // Older Maven builds may still have the native libraries under target/classes.
            if(resource!=null&&resource.getProtocol().equals("file"))
                System.load(Path.of(resource.toURI()).toAbsolutePath().toString());
            else Loader.loadNativeLibraries();
            loaded=true;
        }catch(URISyntaxException | RuntimeException | LinkageError ex){
            throw new IllegalStateException("Cannot load the OR-Tools runtime for "+Platform.RESOURCE_PREFIX+". Reload Maven dependencies and run mvn javafx:run with JDK 21.",ex);
        }
    }
}
