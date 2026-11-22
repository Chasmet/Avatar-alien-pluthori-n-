import com.sun.source.util.JavacTask;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import javax.tools.*;

/** Syntax only; no Android SDK required. */
public final class ParseJavaSources {
    public static void main(String[] args) throws Exception {
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        if(compiler==null)throw new IllegalStateException("JDK required");
        Path source=Paths.get("app/src/main/java");
        List<File> sources=new ArrayList<>();
        try(java.util.stream.Stream<Path> paths=Files.walk(source)){
            paths.filter(p->p.toString().endsWith(".java")).forEach(p->sources.add(p.toFile()));
        }
        DiagnosticCollector<JavaFileObject> errors=new DiagnosticCollector<>();
        try(StandardJavaFileManager fm=compiler.getStandardFileManager(errors,null,null)){
            JavacTask task=(JavacTask)compiler.getTask(null,fm,errors,
                Arrays.asList("--release","17"),null,fm.getJavaFileObjectsFromFiles(sources));
            task.parse();
        }
        if(!errors.getDiagnostics().isEmpty()){
            boolean fatal=false;
            for(Diagnostic<? extends JavaFileObject> e:errors.getDiagnostics()){
                if(e.getKind()==Diagnostic.Kind.ERROR){fatal=true;System.err.println(e);}
            }
            if(fatal)System.exit(1);
        }
        System.out.println("Java 17 syntax OK ("+sources.size()+" fichiers). Type-check Android requiert Gradle.");
    }
}
