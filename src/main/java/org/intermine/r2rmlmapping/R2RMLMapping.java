package org.intermine.r2rmlmapping;

import org.intermine.metadata.*;
import org.intermine.sql.DatabaseUtil;

import java.util.HashSet;
import java.util.Set;

public class R2RMLMapping {
    /** The version number of the database format */
    static final int FORMAT_VERSION = 1;

    public static void main(String[] args) {

        Model model = Model.getInstanceByName("genomic");
        Set<ClassDescriptor> classDescriptors = model.getClassDescriptors();
        Set<CollectionDescriptor> indirections = new HashSet<CollectionDescriptor>();

        for (ClassDescriptor cd :classDescriptors) {
            String table = cd.getSimpleName();
            if (table.equalsIgnoreCase("Gene") ||
                    table.equalsIgnoreCase("Chromosome") ||
                    table.equalsIgnoreCase("Organism") ||
                    table.equalsIgnoreCase("DataSource") ||
                    table.equalsIgnoreCase("DataSet")) {

                System.out.println("TABLE: " + DatabaseUtil.getTableName(cd));
                for (FieldDescriptor fd : cd.getAllFieldDescriptors()) {
                    String columnName = DatabaseUtil.getColumnName(fd);
                    if (fd instanceof AttributeDescriptor) {
                        System.out.println(columnName +
                                (columnName.equalsIgnoreCase("id") ? ": PRIMARY KEY" : ": column")
                                        + " with type " + ((AttributeDescriptor) fd).getType());
                    } else if (!fd.isCollection()) { //n to one relation
                        System.out.println(columnName + ": FOREIGN KEY with type java.lang.Integer referring to "
                            + ((ReferenceDescriptor) fd).getReferencedClassDescriptor().getSimpleName() + ".id");
                    }
                }
                System.out.println();

                //joining table
                for (CollectionDescriptor collection : cd.getCollectionDescriptors()) {
                    if (FieldDescriptor.M_N_RELATION == collection.relationType()) {
                        if (!indirections.contains(collection.getReverseReferenceDescriptor())) {
                            indirections.add(collection);
                            String indirectionTable = DatabaseUtil.getIndirectionTableName(collection);
                            System.out.println("JOINING TABLE: " + indirectionTable);
                            String column1 = DatabaseUtil.getInwardIndirectionColumnName(collection, FORMAT_VERSION);
                            String column2 = DatabaseUtil.getOutwardIndirectionColumnName(collection, FORMAT_VERSION);
                            System.out.println(column1 + ": FOREIGN KEY with type java.lang.Integer referring to "
                                    + table + ".id");
                            System.out.println(column2 + ": FOREIGN KEY with type java.lang.Integer referring to "
                                    + collection.getReferencedClassDescriptor().getName() + (".id"));
                            System.out.println();
                        }
                    }

                }
            }
        }
    }
}
