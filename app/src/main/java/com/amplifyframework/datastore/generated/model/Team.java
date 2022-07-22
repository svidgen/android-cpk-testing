package com.amplifyframework.datastore.generated.model;

import com.amplifyframework.core.model.annotations.BelongsTo;
import com.amplifyframework.core.model.temporal.Temporal;
import com.amplifyframework.core.model.ModelIdentifier;

import java.util.List;
import java.util.UUID;
import java.util.Objects;

import androidx.core.util.ObjectsCompat;

import com.amplifyframework.core.model.Model;
import com.amplifyframework.core.model.annotations.Index;
import com.amplifyframework.core.model.annotations.ModelConfig;
import com.amplifyframework.core.model.annotations.ModelField;
import com.amplifyframework.core.model.query.predicate.QueryField;

import static com.amplifyframework.core.model.query.predicate.QueryField.field;

/** This is an auto generated class representing the Team type in your schema. */
@SuppressWarnings("all")
@ModelConfig(pluralName = "Teams", type = Model.Type.USER, version = 1)
@Index(name = "undefined", fields = {"teamId","name"})
public final class Team implements Model {
  public static final QueryField TEAM_ID = field("Team", "teamId");
  public static final QueryField NAME = field("Team", "name");
  public static final QueryField PROJECT = field("Team", "teamProjectProjectId");
  private final @ModelField(targetType="ID", isRequired = true) String teamId;
  private final @ModelField(targetType="String", isRequired = true) String name;
  private final @ModelField(targetType="Project") @BelongsTo(targetName = "teamProjectProjectId", targetNames = {"teamProjectProjectId", "teamProjectName"}, type = Project.class) Project project;
  private @ModelField(targetType="AWSDateTime", isReadOnly = true) Temporal.DateTime createdAt;
  private @ModelField(targetType="AWSDateTime", isReadOnly = true) Temporal.DateTime updatedAt;
  private TeamIdentifier teamIdentifier;
  public TeamIdentifier resolveIdentifier() {
    if (teamIdentifier == null) {
      this.teamIdentifier = new TeamIdentifier(teamId, name);
    }
    return teamIdentifier;
  }
  
  public String getTeamId() {
      return teamId;
  }
  
  public String getName() {
      return name;
  }
  
  public Project getProject() {
      return project;
  }
  
  public Temporal.DateTime getCreatedAt() {
      return createdAt;
  }
  
  public Temporal.DateTime getUpdatedAt() {
      return updatedAt;
  }
  
  private Team(String teamId, String name, Project project) {
    this.teamId = teamId;
    this.name = name;
    this.project = project;
  }
  
  @Override
   public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      } else if(obj == null || getClass() != obj.getClass()) {
        return false;
      } else {
      Team team = (Team) obj;
      return ObjectsCompat.equals(getTeamId(), team.getTeamId()) &&
              ObjectsCompat.equals(getName(), team.getName()) &&
              ObjectsCompat.equals(getProject(), team.getProject()) &&
              ObjectsCompat.equals(getCreatedAt(), team.getCreatedAt()) &&
              ObjectsCompat.equals(getUpdatedAt(), team.getUpdatedAt());
      }
  }
  
  @Override
   public int hashCode() {
    return new StringBuilder()
      .append(getTeamId())
      .append(getName())
      .append(getProject())
      .append(getCreatedAt())
      .append(getUpdatedAt())
      .toString()
      .hashCode();
  }
  
  @Override
   public String toString() {
    return new StringBuilder()
      .append("Team {")
      .append("teamId=" + String.valueOf(getTeamId()) + ", ")
      .append("name=" + String.valueOf(getName()) + ", ")
      .append("project=" + String.valueOf(getProject()) + ", ")
      .append("createdAt=" + String.valueOf(getCreatedAt()) + ", ")
      .append("updatedAt=" + String.valueOf(getUpdatedAt()))
      .append("}")
      .toString();
  }
  
  public static TeamIdStep builder() {
      return new Builder();
  }
  
  public CopyOfBuilder copyOfBuilder() {
    return new CopyOfBuilder(teamId,
      name,
      project);
  }
  public interface TeamIdStep {
    NameStep teamId(String teamId);
  }
  

  public interface NameStep {
    BuildStep name(String name);
  }
  

  public interface BuildStep {
    Team build();
    BuildStep project(Project project);
  }
  

  public static class Builder implements TeamIdStep, NameStep, BuildStep {
    private String teamId;
    private String name;
    private Project project;
    @Override
     public Team build() {
        
        return new Team(
          teamId,
          name,
          project);
    }
    
    @Override
     public NameStep teamId(String teamId) {
        Objects.requireNonNull(teamId);
        this.teamId = teamId;
        return this;
    }
    
    @Override
     public BuildStep name(String name) {
        Objects.requireNonNull(name);
        this.name = name;
        return this;
    }
    
    @Override
     public BuildStep project(Project project) {
        this.project = project;
        return this;
    }
  }
  

  public final class CopyOfBuilder extends Builder {
    private CopyOfBuilder(String teamId, String name, Project project) {
      super.teamId(teamId)
        .name(name)
        .project(project);
    }
    
    @Override
     public CopyOfBuilder teamId(String teamId) {
      return (CopyOfBuilder) super.teamId(teamId);
    }
    
    @Override
     public CopyOfBuilder name(String name) {
      return (CopyOfBuilder) super.name(name);
    }
    
    @Override
     public CopyOfBuilder project(Project project) {
      return (CopyOfBuilder) super.project(project);
    }
  }
  

  public static class TeamIdentifier extends ModelIdentifier<Team> {
    private static final long serialVersionUID = 1L;
    public TeamIdentifier(String teamId, String name) {
      super(teamId, name);
    }
  }
  
}
