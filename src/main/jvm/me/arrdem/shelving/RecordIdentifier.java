package me.arrdem.shelving;

import clojure.lang.*;

import java.util.Objects;
import java.util.UUID;
import static clojure.lang.RT.cons;

public class RecordIdentifier implements clojure.lang.Seqable {
    public final UUID id;
    public final Keyword spec;

    public RecordIdentifier(Keyword spec, UUID id) {
        this.spec = spec;
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RecordIdentifier that = (RecordIdentifier) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(spec, that.spec);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    public String toString() {
        return String.format("#shelving/id [%s %s]", this.spec, this.id);
    }

    @Override
    public ISeq seq() {
        return cons(this.spec, cons(this.id, null));
    }
}
