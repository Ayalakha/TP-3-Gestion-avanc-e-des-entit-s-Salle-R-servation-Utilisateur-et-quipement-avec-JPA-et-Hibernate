package com.example.model;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Salon (toplantı odası) varlığı.
 * - Bir salonun birden fazla rezervasyonu olabilir (OneToMany).
 * - Bir salon birden fazla ekipman içerebilir ve bir ekipman birden fazla
 *   salonda bulunabilir (ManyToMany).
 */
@Entity
public class Salle {

    // Birincil anahtar, otomatik üretilir
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Salon adı zorunlu ve benzersiz
    @Column(nullable = false, unique = true)
    private String nom;

    // Salonun kişi kapasitesi
    private int capacite;

    /*
     * Rezervasyonlar ilişkisinin ters tarafı.
     * Burada kademeli işlem (cascade) YOKTUR: rezervasyonların yaşam döngüsünü
     * Utilisateur yönetir. Böylece bir kullanıcı silindiğinde Hibernate'in
     * "silinen nesne cascade ile yeniden kaydedilecek" hatası oluşmaz.
     */
    @OneToMany(mappedBy = "salle")
    private Set<Reservation> reservations = new HashSet<>();

    /*
     * ManyToMany ilişkisinin sahibi (owning side) Salle'dir.
     * @JoinTable -> "salle_equipement" adlı ara (birleştirme) tablosunu tanımlar.
     * cascade PERSIST ve MERGE -> yeni ekipmanlar salonla birlikte kaydedilir/güncellenir.
     * REMOVE KULLANILMAZ: paylaşılan bir ekipmanın yanlışlıkla silinmesini önlemek için.
     */
    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "salle_equipement",
            joinColumns = @JoinColumn(name = "salle_id"),
            inverseJoinColumns = @JoinColumn(name = "equipement_id")
    )
    private Set<Equipement> equipements = new HashSet<>();

    // JPA için zorunlu boş yapıcı
    public Salle() {
    }

    public Salle(String nom, int capacite) {
        this.nom = nom;
        this.capacite = capacite;
    }

    /**
     * Ekipman ekler ve ilişkinin her iki tarafını da günceller (çift yönlü tutarlılık).
     */
    public void addEquipement(Equipement equipement) {
        equipements.add(equipement);
        equipement.getSalles().add(this);
    }

    /**
     * Ekipmanı salondan çıkarır (ekipmanın kendisi silinmez, sadece ara tablodaki satır silinir).
     */
    public void removeEquipement(Equipement equipement) {
        equipements.remove(equipement);
        equipement.getSalles().remove(this);
    }

    /**
     * Ek alıştırma: salonun verilen zaman aralığında müsait olup olmadığını kontrol eder.
     * İki aralık çakışıyorsa (mevcutBaşlangıç < yeniBitiş VE yeniBaşlangıç < mevcutBitiş)
     * salon müsait değildir.
     */
    public boolean estDisponible(LocalDateTime debut, LocalDateTime fin) {
        for (Reservation r : reservations) {
            boolean chevauchement = r.getDateDebut().isBefore(fin) && debut.isBefore(r.getDateFin());
            if (chevauchement) {
                return false; // Çakışan bir rezervasyon bulundu
            }
        }
        return true; // Hiçbir çakışma yok, salon müsait
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

    public int getCapacite() {
        return capacite;
    }

    public void setCapacite(int capacite) {
        this.capacite = capacite;
    }

    public Set<Reservation> getReservations() {
        return reservations;
    }

    public Set<Equipement> getEquipements() {
        return equipements;
    }

    @Override
    public String toString() {
        return "Salle{id=" + id + ", nom='" + nom + "', capacite=" + capacite + "}";
    }
}
