package metrics.maintainability.cyclomatic_complexity;

public class TryCatch2 {
    void func1(){
        int a = 0;
        try {
            if(a){
                
            }
        } catch (Exception e) {
            if(a){

            }
        }
    }
}
