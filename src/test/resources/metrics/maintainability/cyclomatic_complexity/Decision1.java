package metrics.maintainability.cyclomatic_complexity;

public class Decision1 {
    void func1(){
        int i = 1;
        if(i){
           if(i) return;

           return;
        }

        if(i) return;

        return;
    }
}
