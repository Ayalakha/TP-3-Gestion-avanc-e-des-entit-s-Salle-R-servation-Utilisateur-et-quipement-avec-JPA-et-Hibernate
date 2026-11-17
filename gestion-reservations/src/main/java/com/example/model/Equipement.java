package com.example.model;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Ekipman varlığı (projeksiyon, beyaz tahta vb.).
 * Bir ekipman birden fazla salonda bulunabilir (ManyToMany).
 */
@Entity
public class Equipement {

    // Birincil anahtar, otomatik üretilir
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Ekipman adı zorunlu ve benzersiz
    @Column(nullable = false, unique = true)
    private String nom;

    /*
     * ManyToMany ilişkisinin ters tarafı: mappedBy = "equipements"
     * ara tabloyu Salle sınıfının yönettiğini belirtir.
     */
    @ManyToMany(mappedBy = "equipements")
    private Set<Salle> salles = new HashSet<>();

    // JPA için zorunlu boş yapıcı
    public Equipement() {
    }

    public Equipement(String nom) {
        this.nom = nom;
    }

    // ----- Getter ve Setter metotları -----

    public Long getId() {
        return id;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public Set<Salle> getSalles() {
        return salles;
    }

    @Override
    public String toString() {
        return "Equipement{id=" + id + ", nom='" + nom + "'}";
    }
}
