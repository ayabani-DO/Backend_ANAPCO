package tn.esprit.examen.nomPrenomClasseExamen.entities;

public enum SeverityCode {
    LOW(1), MEDIUM(2), HIGH(3), CRITICAL(4);
    
    private final int weight;
    
    SeverityCode(int weight) {
        this.weight = weight;
    }
    
    public int getWeight() {
        return weight;
    }
}
