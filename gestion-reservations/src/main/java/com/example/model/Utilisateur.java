package com.example.model;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Kullanıcı varlığı (entity).
 * Bir kullanıcının birden fazla rezervasyonu olabilir (OneToMany).
 */
@Entity
public class Utilisateur {

    // Birincil anahtar: veritabanı tarafından otomatik artırılır (IDENTITY)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // E-posta zorunludur ve benzersiz olmalıdır
    @Column(nullable = false, unique = true)
    private String email;

    // Ad soyad zorunludur
    @Column(nullable = false)
    private String nom;

    /*
     * İlişkinin ters tarafı (inverse side): yabancı anahtar Reservation tablosundadır.
     * cascade = ALL      -> persist, merge, remove... işlemleri rezervasyonlara da uygulanır.
     * orphanRemoval=true -> koleksiyondan çıkarılan (sahipsiz kalan) rezervasyon veritabanından silinir.
     */
    @OneToMany(mappedBy = "utilisateur", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Reservation> reservations = new HashSet<>();

    // JPA için zorunlu parametresiz yapıcı (constructor)
    public Utilisateur() {
    }

    // Kolaylık sağlayan yapıcı
    public Utilisateur(String email, String nom) {
        this.email = email;
        this.nom = nom;
    }

    /**
     * Rezervasyon ekler ve ilişkinin iki tarafını da senkronize eder.
     * Koleksiyonu doğrudan değiştirmek yerine bu metot kullanılmalıdır.
     */
    public void addReservation(Reservation reservation) {
        reservations.add(reservation);
        reservation.setUtilisateur(this);
    }

    /**
     * Rezervasyonu kaldırır. orphanRemoval sayesinde commit sırasında
     * rezervasyon veritabanından da silinir.
     */
    public void removeReservation(Reservation reservation) {
        reservations.remove(reservation);
        // Salonun koleksiyonundan da çıkar ki bellekteki model tutarlı kalsın
        if (reservation.getSalle() != null) {
            reservation.getSalle().getReservations().remove(reservation);
        }
    }

    // ----- Getter ve Setter metotları -----

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public Set<Reservation> getReservations() {
        return reservations;
    }

    // toString içinde ilişkili koleksiyonlar yazdırılmaz (sonsuz döngü ve lazy yükleme riskini önler)
    @Override
    public String toString() {
        return "Utilisateur{id=" + id + ", email='" + email + "', nom='" + nom + "'}";
    }
}
