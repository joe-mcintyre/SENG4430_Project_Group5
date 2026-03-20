package metrics.maintainability.cyclomatic_complexity;

public class Complex2 {
    // CC 5
    void func1(){
        int a = 1;
        if(a){
            switch (key) {
                case value:
                    for(int i = 0; i < 10; i++){

                    }
                    break;
            
                default:
                    break;
            }
           return;
        }
        return;
    }

    // CC 6
    void func2(){
        try {
            int a = 1;
            if(a){
                switch (key) {
                    case value:
                        for(int i = 0; i < 10; i++){

                        }
                        break;
                
                    default:
                        break;
                }
                return;
            }
            return;
        } catch (Exception e) {
            // TODO: handle exception
        }
      
    }
}
