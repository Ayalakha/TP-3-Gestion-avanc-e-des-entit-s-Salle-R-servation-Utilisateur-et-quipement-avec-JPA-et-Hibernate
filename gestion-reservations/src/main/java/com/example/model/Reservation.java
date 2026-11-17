package com.example.model;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Rezervasyon varlığı.
 * Her rezervasyon tek bir kullanıcıya ve tek bir salona aittir (ManyToOne).
 */
@Entity
public class Reservation {

    // Birincil anahtar, otomatik üretilir
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Rezervasyonun başlangıç ve bitiş tarihleri (zorunlu)
    @Column(nullable = false)
    private LocalDateTime dateDebut;

    @Column(nullable = false)
    private LocalDateTime dateFin;

    // Rezervasyonun amacı (isteğe bağlı)
    private String motif;

    /*
     * İlişkinin sahibi (owning side): yabancı anahtar "utilisateur_id" bu tablodadır.
     * nullable = false -> kullanıcısız rezervasyon olamaz.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    /*
     * Rezervasyonun ait olduğu salon. cascade PERSIST/MERGE sayesinde yeni bir salon,
     * rezervasyon kaydedilirken otomatik olarak kaydedilir.
     * REMOVE yok: rezervasyon silinince salon silinmemeli.
     */
    @ManyToOne(optional = false, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(name = "salle_id", nullable = false)
    private Salle salle;

    // JPA için zorunlu boş yapıcı
    public Reservation() {
    }

    public Reservation(LocalDateTime dateDebut, LocalDateTime dateFin, String motif) {
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
        this.motif = motif;
    }

    /**
     * Ek alıştırma: tarih doğrulaması.
     * Kaydetmeden (persist) ve güncellemeden (update) önce otomatik çağrılır.
     * Başlangıç tarihi bitiş tarihinden önce değilse istisna fırlatılır.
     */
    @PrePersist
    @PreUpdate
    private void validerDates() {
        if (dateDebut == null || dateFin == null || !dateDebut.isBefore(dateFin)) {
            throw new IllegalStateException(
                    "Geçersiz tarihler: başlangıç tarihi bitiş tarihinden önce olmalıdır ("
                            + dateDebut + " -> " + dateFin + ")");
        }
    }

    // ----- Getter ve Setter metotları -----

    public Long getId() {
        return id;
    }

    public LocalDateTime getDateDebut() {
        return dateDebut;
    }

    public void setDateDebut(LocalDateTime dateDebut) {
        this.dateDebut = dateDebut;
    }

    public LocalDateTime getDateFin() {
        return dateFin;
    }

    public void setDateFin(LocalDateTime dateFin) {
        this.dateFin = dateFin;
    }

    public String getMotif() {
        return motif;
    }

    public void setMotif(String motif) {
        this.motif = motif;
    }

    public Utilisateur getUtilisateur() {
        return utilisateur;
    }

    public void setUtilisateur(Utilisateur utilisateur) {
        this.utilisateur = utilisateur;
    }

    public Salle getSalle() {
        return salle;
    }

    /**
     * Salonu atar ve salonun rezervasyon koleksiyonunu da günceller
     * (iki taraf da senkronize kalır).
     */
    public void setSalle(Salle salle) {
        if (this.salle != null) {
            this.salle.getReservations().remove(this);
        }
        this.salle = salle;
        if (salle != null) {
            salle.getReservations().add(this);
        }
    }

    @Override
    public String toString() {
        return "Reservation{id=" + id + ", debut=" + dateDebut + ", fin=" + dateFin
                + ", motif='" + motif + "'}";
    }
}
