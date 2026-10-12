package com.acme.tenancy.config;
import com.acme.tenancy.entities.*;
import com.acme.tenancy.repositories.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
@Configuration
public class DemoData {
 @Bean CommandLineRunner seed(UserRepository users,TenancyRepository tenancies,MembershipRepository memberships,WorkItemRepository items,PasswordEncoder passwords,@Value("${app.seed.enabled:true}") boolean enabled) { return args->{ if(!enabled||users.existsByEmail("root@example.test")) return; seedData(users,tenancies,memberships,items,passwords); }; }
 @Transactional void seedData(UserRepository users,TenancyRepository tenancies,MembershipRepository memberships,WorkItemRepository items,PasswordEncoder passwords) {
  users.save(new UserAccount("root@example.test","Superadmin",passwords.encode("ChangeMe123!"),true));
  var user1=users.save(new UserAccount("user1@email.com","User 1",passwords.encode("ChangeMe123!"),false));
  var user2=users.save(new UserAccount("user2@email.com","User 2",passwords.encode("ChangeMe123!"),false));
  var company1=tenancies.save(new Tenancy("Company 1","company1")); var company2=tenancies.save(new Tenancy("Company 2","company2"));
  memberships.save(new Membership(user1,company1,MembershipRole.ADMIN)); memberships.save(new Membership(user2,company1,MembershipRole.MEMBER)); memberships.save(new Membership(user1,company2,MembershipRole.MEMBER));
  items.save(new WorkItem(company1,"Prepare quarterly report","Collect metrics and draft the customer report.")); items.save(new WorkItem(company1,"Review onboarding flow","Check the tenant onboarding steps.") ); items.save(new WorkItem(company2,"Plan integration","Review API integration requirements."));
 }
}
