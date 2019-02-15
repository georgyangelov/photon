Maybe.include module
  def to_bool(self: Maybe)
    present?
  end

  def unwrap_or(self: Maybe, other: _)
    if present?
      value
    else
      other
    end
  end
end

Struct.include module
  def include(self: Struct, other: Module)
    $module.include other

    self
  end
end
