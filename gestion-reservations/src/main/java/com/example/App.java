package com.example;

import com.example.model.*;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/**
 * Varlıkları ve ilişkileri test eden ana sınıf.
 * Her senaryo ayrı bir EntityManager ile çalışır; böylece veriler
 * gerçekten veritabanından yeniden okunur.
 */
public class App {

    // Tüm uygulama için tek bir EntityManagerFactory (oluşturması maliyetlidir)
    private static EntityManagerFactory emf;

    public static void main(String[] args) {
        // persistence.xml içindeki kalıcılık birimini yükle
        emf = Persistence.createEntityManagerFactory("gestion-reservations-pu");

        try {
            Long[] ids = creerDonnees();          // 1) Varlıkları oluştur ve kaydet
            Long aliceId = ids[0];

            requetesJpql();                       // 2) JPQL sorguları
            testerDisponibilite();                // 3) Salon müsaitlik kontrolü
            testerValidationDates(aliceId);       // 4) Tarih doğrulaması
            testerOrphanRemoval(aliceId);         // 5) Sahipsiz rezervasyonun silinmesi
            testerSuppressionUtilisateur(aliceId);// 6) Kullanıcının kademeli silinmesi
        } finally {
            // Fabrikayı her durumda kapat
            emf.close();
        }
    }

    /**
     * 1) Kullanıcılar, salonlar, ekipmanlar ve rezervasyonlar oluşturulur.
     * Sadece kullanıcılar persist edilir; geri kalan her şey cascade ile kaydedilir.
     */
    private static Long[] creerDonnees() {
        titre("1) Varlıkların oluşturulması ve cascade ile kaydedilmesi");

        Utilisateur alice = new Utilisateur("alice@example.com", "Alice Dupont");
        Utilisateur bob = new Utilisateur("bob@example.com", "Bob Martin");

        // Ekipmanlar: "Projecteur" iki salon arasında paylaşılıyor
        Equipement projecteur = new Equipement("Projecteur");
        Equipement tableau = new Equipement("Tableau blanc");
        Equipement visio = new Equipement("Visioconférence");

        Salle salleA = new Salle("Salle A", 20);
        salleA.addEquipement(projecteur);
        salleA.addEquipement(tableau);

        Salle salleB = new Salle("Salle B", 8);
        salleB.addEquipement(projecteur);   // Paylaşılan ekipman
        salleB.addEquipement(visio);

        LocalDateTime demain = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0);

        // Alice'in iki rezervasyonu
        Reservation r1 = new Reservation(demain, demain.plusHours(2), "Réunion d'équipe");
        r1.setSalle(salleA);
        alice.addReservation(r1);

        Reservation r2 = new Reservation(demain.plusHours(3), demain.plusHours(4), "Appel client");
        r2.setSalle(salleB);
        alice.addReservation(r2);

        // Bob'un bir rezervasyonu
        Reservation r3 = new Reservation(demain.plusDays(1), demain.plusDays(1).plusHours(1), "Formation");
        r3.setSalle(salleA);
        bob.addReservation(r3);

        executer(em -> {
            // Utilisateur -> Reservation (ALL) -> Salle (PERSIST) -> Equipement (PERSIST)
            em.persist(alice);
            em.persist(bob);
        });

        System.out.println(">> Kaydedildi: " + alice + ", " + bob);
        return new Long[]{alice.getId(), bob.getId()};
    }

    /**
     * 2) Ek alıştırma: JPQL sorguları.
     */
    private static void requetesJpql() {
        titre("2) JPQL sorguları");

        executer(em -> {
            // Bir kullanıcının rezervasyonları (tarihe göre sıralı)
            List<Reservation> resAlice = em.createQuery(
                            "SELECT r FROM Reservation r WHERE r.utilisateur.email = :email ORDER BY r.dateDebut",
                            Reservation.class)
                    .setParameter("email", "alice@example.com")
                    .getResultList();
            System.out.println(">> Alice'in rezervasyonları: " + resAlice);

            // Belirli bir ekipmana sahip salonlar (JOIN ile ara tablo üzerinden)
            List<Salle> sallesAvecProjecteur = em.createQuery(
                            "SELECT DISTINCT s FROM Salle s JOIN s.equipements e WHERE e.nom = :nom",
                            Salle.class)
                    .setParameter("nom", "Projecteur")
                    .getResultList();
            System.out.println(">> Projeksiyonlu salonlar: " + sallesAvecProjecteur);
        });
    }

    /**
     * 3) Ek alıştırma: bir salonun belirli bir zaman aralığında müsait olup olmadığı.
     */
    private static void testerDisponibilite() {
        titre("3) Salon müsaitlik kontrolü");

        executer(em -> {
            Salle salleA = em.createQuery("SELECT s FROM Salle s WHERE s.nom = :nom", Salle.class)
                    .setParameter("nom", "Salle A")
                    .getSingleResult();

            LocalDateTime demain9h = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0);

            // 10:00-11:00 aralığı, 09:00-11:00 rezervasyonuyla çakışır -> müsait değil
            System.out.println(">> Salle A yarın 10:00-11:00 müsait mi? "
                    + salleA.estDisponible(demain9h.plusHours(1), demain9h.plusHours(2)));

            // 14:00-15:00 aralığında rezervasyon yok -> müsait
            System.out.println(">> Salle A yarın 14:00-15:00 müsait mi? "
                    + salleA.estDisponible(demain9h.plusHours(5), demain9h.plusHours(6)));

            // Aynı kontrol doğrudan veritabanında JPQL ile de yapılabilir
            Long nbConflits = em.createQuery(
                            "SELECT COUNT(r) FROM Reservation r WHERE r.salle = :salle "
                                    + "AND r.dateDebut < :fin AND :debut < r.dateFin", Long.class)
                    .setParameter("salle", salleA)
                    .setParameter("debut", demain9h.plusHours(1))
                    .setParameter("fin", demain9h.plusHours(2))
                    .getSingleResult();
            System.out.println(">> JPQL ile çakışan rezervasyon sayısı: " + nbConflits);
        });
    }

    /**
     * 4) Ek alıştırma: geçersiz tarihli bir rezervasyon @PrePersist tarafından reddedilir.
     */
    private static void testerValidationDates(Long utilisateurId) {
        titre("4) Tarih doğrulaması (başlangıç < bitiş)");

        executer(em -> {
            Utilisateur alice = em.find(Utilisateur.class, utilisateurId);
            Salle salleB = em.createQuery("SELECT s FROM Salle s WHERE s.nom = 'Salle B'", Salle.class)
                    .getSingleResult();

            LocalDateTime debut = LocalDateTime.now().plusDays(5);
            // Bitiş tarihi başlangıçtan ÖNCE -> geçersiz
            Reservation invalide = new Reservation(debut, debut.minusHours(1), "Rezervasyon hatalı");
            invalide.setSalle(salleB);
            alice.addReservation(invalide);
            em.persist(invalide); // Burada IllegalStateException fırlatılır, işlem geri alınır
        });
    }

    /**
     * 5) orphanRemoval testi: koleksiyondan çıkarılan rezervasyon veritabanından silinir.
     */
    private static void testerOrphanRemoval(Long utilisateurId) {
        titre("5) orphanRemoval: rezervasyonun koleksiyondan çıkarılması");

        executer(em -> {
            Utilisateur alice = em.find(Utilisateur.class, utilisateurId);
            Reservation res = alice.getReservations().iterator().next();
            System.out.println(">> Kaldırılacak rezervasyon: " + res);
            // em.remove() çağrılmaz; koleksiyondan çıkarmak yeterlidir -> commit'te DELETE üretilir
            alice.removeReservation(res);
        });

        executer(em -> {
            Long nb = em.createQuery("SELECT COUNT(r) FROM Reservation r WHERE r.utilisateur.id = :id", Long.class)
                    .setParameter("id", utilisateurId)
                    .getSingleResult();
            System.out.println(">> Alice'in kalan rezervasyon sayısı: " + nb);
        });
    }

    /**
     * 6) Kullanıcı silinince rezervasyonları cascade ile silinir;
     * salonlar ve paylaşılan ekipmanlar ise korunur (REMOVE cascade yok).
     */
    private static void testerSuppressionUtilisateur(Long utilisateurId) {
        titre("6) Kullanıcının silinmesi (rezervasyonlarda cascade REMOVE)");

        executer(em -> {
            Utilisateur alice = em.find(Utilisateur.class, utilisateurId);
            em.remove(alice); // Alice ve tüm rezervasyonları silinir
        });

        executer(em -> {
            long nbUtilisateurs = em.createQuery("SELECT COUNT(u) FROM Utilisateur u", Long.class).getSingleResult();
            long nbReservations = em.createQuery("SELECT COUNT(r) FROM Reservation r", Long.class).getSingleResult();
            long nbSalles = em.createQuery("SELECT COUNT(s) FROM Salle s", Long.class).getSingleResult();
            long nbEquipements = em.createQuery("SELECT COUNT(e) FROM Equipement e", Long.class).getSingleResult();

            System.out.println(">> Kalan kullanıcılar   : " + nbUtilisateurs + " (sadece Bob)");
            System.out.println(">> Kalan rezervasyonlar : " + nbReservations + " (Bob'unki)");
            System.out.println(">> Kalan salonlar       : " + nbSalles + " (silinmedi)");
            System.out.println(">> Kalan ekipmanlar     : " + nbEquipements + " (paylaşılan ekipmanlar korundu)");
        });
    }

    /**
     * Ek alıştırma: sağlam hata yönetimi.
     * Verilen işlemi bir transaction içinde çalıştırır; hata olursa geri alır (rollback)
     * ve EntityManager'ı her durumda kapatır.
     */
    private static void executer(Consumer<EntityManager> action) {
        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            action.accept(em);
            tx.commit();
        } catch (RuntimeException e) {
            // İşlem aktifse yapılan değişiklikleri geri al
            if (tx.isActive()) {
                tx.rollback();
            }
            System.out.println("!! Hata yakalandı, işlem geri alındı: " + causeRacine(e).getMessage());
        } finally {
            em.close();
        }
    }

    // İstisna zincirindeki en alttaki (asıl) nedeni bulur
    private static Throwable causeRacine(Throwable t) {
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t;
    }

    // Konsolda okunabilir bölüm başlığı yazdırır
    private static void titre(String texte) {
        System.out.println("\n==================== " + texte + " ====================");
    }
}
